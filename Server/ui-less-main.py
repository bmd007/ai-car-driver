import sys
import struct
import time
import signal
import math
import threading
import multiprocessing
from server import Server
from message import Message_Parse
from command import Command
from led import Led
from camera import Camera
from car import Car
from buzzer import Buzzer
from Thread import stop_thread
from threading import Thread

class ServerController:
    def __init__(self):
        self.running = True
        self.config_task()
        signal.signal(signal.SIGINT, self.signal_handler)
        # Auto-start server
        self.start_server()

    def config_task(self):
        self.tcp_server = Server()
        self.command = Command()
        self.led = Led()
        self.car = Car()
        self.buzzer = Buzzer()
        self.camera = Camera(stream_size=(400, 300))
        self.queue_cmd = multiprocessing.Queue()
        self.cmd_parse = Message_Parse()
        self.queue_led = multiprocessing.Queue()
        self.led_parse = Message_Parse()

        self.cmd_thread = None
        self.video_thread = None
        self.car_thread = None
        self.led_process = None
        self.cmd_thread_is_running = False
        self.video_thread_is_running = False
        self.car_thread_is_running = False
        self.led_process_is_running = False
        self.car_mode = 1
        self.rotation_flag = False
        self.send_sonic_data_time = time.time()
        self.send_light_data_time = time.time()
        self.send_line_data_time = time.time()
        self.led_mode = 0

    def start_server(self):
        print("Starting server...")
        self.tcp_server.start_tcp_servers()
        self.set_threading_cmd_receive(True)
        self.set_threading_video_send(True)
        self.set_threading_car_task(True)
        self.set_process_led_running(True)
        print("Server started successfully")

    def stop_server(self):
        print("Stopping server...")
        self.tcp_server.stop_tcp_servers()
        self.set_threading_cmd_receive(False)
        self.set_threading_video_send(False)
        self.set_threading_car_task(False)
        self.set_process_led_running(False)
        self.stop_car()
        print("Server stopped")

    def stop_car(self):
        self.led.colorBlink(0)
        self.camera.stop_stream()
        self.camera.close()
        self.car.close()

    def send_sonic_data(self):
        if time.time() - self.send_sonic_data_time > 0.5:
            self.send_sonic_data_time = time.time()
            if self.tcp_server.get_command_server_busy() == False:
                distance = self.car.sonic.get_distance()
                cmd = self.command.CMD_MODE + "#3#{:.2f}".format(distance) + "\n"
                self.tcp_server.send_data_to_command_client(cmd)

    def send_light_data(self):
        if time.time() - self.send_light_data_time > 0.3:
            self.send_light_data_time = time.time()
            if self.tcp_server.get_command_server_busy() == False:
                adc_light_1 = self.car.adc.read_adc(0)
                adc_light_2 = self.car.adc.read_adc(1)
                cmd = self.command.CMD_MODE + "#2#{:.2f}#{:.2f}".format(adc_light_1, adc_light_2) + "\n"
                self.tcp_server.send_data_to_command_client(cmd)

    def send_line_data(self):
        if time.time() - self.send_line_data_time > 0.3:
            self.send_line_data_time = time.time()
            if self.tcp_server.get_command_server_busy() == False:
                ir_value_1 = self.car.infrared.read_one_infrared(1)
                ir_value_2 = self.car.infrared.read_one_infrared(2)
                ir_value_3 = self.car.infrared.read_one_infrared(3)
                cmd = self.command.CMD_MODE + "#4#{:.2f}#{:.2f}#{:.2f}".format(ir_value_1, ir_value_2, ir_value_3) + "\n"
                self.tcp_server.send_data_to_command_client(cmd)

    def send_power_data(self):
        if self.tcp_server.get_command_server_busy() == False:
            power = self.car.adc.read_adc(2) * (3 if self.car.adc.pcb_version == 1 else 2)
            cmd = self.command.CMD_POWER + "#" + str(power) + "\n"
            self.tcp_server.send_data_to_command_client(cmd)

    def set_threading_cmd_receive(self, state, close_time=0.3):
        if self.cmd_thread is None:
            buf_state = False
        else:
            buf_state = self.cmd_thread.is_alive()
        if state != buf_state:
            if state:
                self.cmd_thread_is_running = True
                self.cmd_thread = threading.Thread(target=self.threading_cmd_receive)
                self.cmd_thread.start()
            else:
                self.cmd_thread_is_running = False
                if self.cmd_thread is not None:
                    self.cmd_thread.join(close_time)
                    self.cmd_thread = None

    def threading_cmd_receive(self):
        while self.cmd_thread_is_running:
            cmd_queue = self.tcp_server.read_data_from_command_server()
            if cmd_queue.qsize() > 0:
                client_address, all_message = cmd_queue.get()
                main_message = all_message.strip()
                if "\n" in main_message:
                    for msg in main_message.split("\n"):
                        self.queue_cmd.put(msg)
                else:
                    self.queue_cmd.put(main_message)
            while not self.queue_cmd.empty():
                msg = self.queue_cmd.get()
                self.cmd_parse.clear_parameters()
                self.cmd_parse.parse(msg)
                print("Command received: {}".format(self.cmd_parse.input_string))
                if self.cmd_parse.command_string == self.command.CMD_LED:
                    self.queue_led.put(msg)
                elif self.cmd_parse.command_string == self.command.CMD_LED_MOD:
                    self.queue_led.put(msg)
                else:
                    if self.cmd_parse.command_string == self.command.CMD_SONIC:
                        self.send_sonic_data()
                    elif self.cmd_parse.command_string == self.command.CMD_LIGHT:
                        self.send_light_data()
                    elif self.cmd_parse.command_string == self.command.CMD_LINE:
                        self.send_line_data()
                    elif self.cmd_parse.command_string == self.command.CMD_POWER:
                        self.send_power_data()
                    elif self.cmd_parse.command_string == self.command.CMD_BUZZER:
                        self.buzzer.set_state(self.cmd_parse.int_parameter[0])
                    elif self.cmd_parse.command_string == self.command.CMD_SERVO:
                        try:
                            data1 = str(self.cmd_parse.int_parameter[0])
                            data2 = int(self.cmd_parse.int_parameter[1])
                            if data1 is not None and data2 is not None:
                                self.car.servo.set_servo_pwm(data1, data2)
                        except Exception as e:
                            print(f"Servo error: {e}")
                    elif self.cmd_parse.command_string == self.command.CMD_MOTOR:
                        self.car_mode = 1
                        try:
                            duty = [int(self.cmd_parse.int_parameter[i]) for i in range(4)]
                            if all(d is not None for d in duty):
                                self.car.motor.set_motor_model(duty[0], duty[1], duty[2], duty[3])
                        except Exception as e:
                            print(f"Motor error: {e}")
                    elif self.cmd_parse.command_string == self.command.CMD_M_MOTOR:
                        self.car_mode = 1
                        duty = [int(self.cmd_parse.int_parameter[i]) for i in range(4)]
                        LX = int((duty[1] * math.sin(math.radians(duty[0]))))
                        LY = int(duty[1] * math.cos(math.radians(duty[0])))
                        RX = int(duty[3] * math.sin(math.radians(duty[2])))
                        RY = int(duty[3] * math.cos(math.radians(duty[2])))
                        FR = LY - LX + RY + RX
                        FL = LY + LX - RY - RX
                        BL = LY - LX - RY + RX
                        BR = LY + LX + RY - RX
                        BR = LY + LX + RY - RX
                        if all(d is not None for d in duty[:3]):
                            self.car.motor.set_motor_model(FL, BL, FR, BR)
                    elif self.cmd_parse.command_string == self.command.CMD_CAR_ROTATE:
                        try:
                            self.car_mode = 1
                            duty = [int(self.cmd_parse.int_parameter[i]) for i in range(4)]
                            if duty[3] == 0:
                                try:
                                    stop_thread(Rotate_Mode)
                                    self.rotation_flag = False
                                except:
                                    pass
                                LX = -int((duty[1] * math.sin(math.radians(duty[0]))))
                                LY = int(duty[1] * math.cos(math.radians(duty[0])))
                                RX = int(duty[3] * math.sin(math.radians(duty[2])))
                                RY = int(duty[3] * math.cos(math.radians(duty[2])))
                                FR = LY - LX + RY + RX
                                FL = LY + LX - RY - RX
                                BL = LY - LX - RY + RX
                                BR = LY + LX + RY - RX
                                if all(d is not None for d in duty[:3]):
                                    self.car.motor.set_motor_model(FL, BL, FR, BR)
                            elif self.rotation_flag == False:
                                try:
                                    stop_thread(self.Rotate_Mode)
                                except:
                                    pass
                                self.rotation_flag = True
                                Rotate_Mode = Thread(target=self.car.mode_rotate, args=(duty[2],))
                                Rotate_Mode.start()
                        except Exception as e:
                            print(f"Rotate error: {e}")
                    elif self.cmd_parse.command_string == self.command.CMD_MODE:
                        mode = self.cmd_parse.int_parameter[0]
                        self.car_mode = mode + 1
                        mode_names = ["Manual", "Light", "Infrared", "Ultrasonic"]
                        if 0 <= mode <= 3:
                            print(f"Car Mode: {mode_names[mode]} Car")
                            if mode == 0:
                                self.car.motor.set_motor_model(0, 0, 0, 0)

            if self.queue_cmd.empty():
                time.sleep(0.001)

    def set_threading_car_task(self, state, close_time=0.3):
        if self.car_thread is None:
            buf_state = False
        else:
            buf_state = self.car_thread.is_alive()
        if state != buf_state:
            if state:
                self.car_thread_is_running = True
                self.car_thread = threading.Thread(target=self.threading_car_task)
                self.car_thread.start()
            else:
                self.car_thread_is_running = False
                if self.car_thread is not None:
                    self.car_thread.join(close_time)
                    self.car_thread = None

    def threading_car_task(self):
        while self.car_thread_is_running:
            if self.car_mode == 1:
                pass
            elif self.car_mode == 2:
                self.car.mode_light()
                self.send_light_data()
            elif self.car_mode == 3:
                self.car.mode_infrared()
            elif self.car_mode == 4:
                self.car.mode_ultrasonic()
                self.send_sonic_data()
            time.sleep(0.01)

    def set_threading_video_send(self, state, close_time=0.3):
        if self.video_thread is None:
            buf_state = False
        else:
            buf_state = self.video_thread.is_alive()
        if state != buf_state:
            if state:
                self.video_thread_is_running = True
                self.video_thread = threading.Thread(target=self.threading_video_send)
                self.video_thread.start()
            else:
                self.video_thread_is_running = False
                if self.video_thread is not None:
                    self.video_thread.join(close_time)
                    self.video_thread = None

    def threading_video_send(self):
        while self.video_thread_is_running:
            if self.tcp_server.is_video_server_connected():
                self.camera.start_stream()
                while self.tcp_server.is_video_server_connected():
                    frame = self.camera.get_frame()
                    lenFrame = len(frame)
                    lengthBin = struct.pack('<I', lenFrame)
                    try:
                        self.tcp_server.send_data_to_video_client(lengthBin)
                        self.tcp_server.send_data_to_video_client(frame)
                    except:
                        break
                self.camera.stop_stream()
            else:
                time.sleep(0.1)

    def set_process_led_running(self, state, close_time=0.3):
        if self.led_process is None:
            buf_state = False
        else:
            buf_state = self.led_process.is_alive()
        if state != buf_state:
            if state:
                self.led_process_is_running = True
                self.led_process = multiprocessing.Process(target=self.process_led_running, args=(self.queue_led,))
                self.led_process.start()
            else:
                self.led_process_is_running = False
                if self.led_process is not None:
                    try:
                        self.led_process.terminate()
                        self.led_process.join(close_time)
                        self.led_process = None
                    except Exception as e:
                        print(f"Error terminating LED process: {e}")

    def process_led_running(self, queue_led):
        try:
            while self.led_process_is_running:
                if not queue_led.empty():
                    queue_buf_cmd = queue_led.get()
                    self.led_parse.clear_parameters()
                    self.led_parse.parse(queue_buf_cmd)
                    print("LED: {}".format(queue_buf_cmd))

                    if self.led_parse.command_string == "CMD_LED" and self.led_mode == 1:
                        try:
                            data = [int(self.led_parse.int_parameter[i]) for i in range(4)]
                            if all(d is not None for d in data):
                                self.led.ledIndex(data[0], data[1], data[2], data[3])
                        except:
                            pass
                    if self.led_parse.command_string == "CMD_LED_MOD":
                        mode = self.led_parse.int_parameter[0]
                        self.led_mode = mode if 0 <= mode <= 5 else 0

                if self.led_mode == 1:
                    pass
                elif self.led_mode == 2:
                    self.led.following()
                elif self.led_mode == 3:
                    self.led.colorBlink(1)
                elif self.led_mode == 4:
                    self.led.rainbowbreathing()
                elif self.led_mode == 5:
                    self.led.rainbowCycle()
                elif self.led_mode == 0:
                    self.led.colorBlink(0)

        except KeyboardInterrupt:
            print("LED process interrupted, cleaning up...")
            self.led.colorBlink(0)

    def signal_handler(self, signal, frame):
        print("Caught Ctrl+C, stopping application...")
        self.running = False
        self.stop_server()
        sys.exit(0)

    def run(self):
        print("Server running. Press Ctrl+C to stop.")
        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            self.signal_handler(signal.SIGINT, None)

if __name__ == '__main__':
    server = ServerController()
    server.run()
