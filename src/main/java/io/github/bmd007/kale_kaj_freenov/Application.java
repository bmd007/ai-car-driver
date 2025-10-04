package io.github.bmd007.kale_kaj_freenov;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;


@RestController
@SpringBootApplication
public class Application {

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final int PCA9685_ADDR = 0x40;
    private static final int I2C_BUS = 1;
    private static final int MODE1 = 0x00;
    private static final int PRESCALE = 0xFE;
    private static final int PWM_FREQ = 50;
    private static final int LED0_ON_L = 0x06;
    private static final int MAX_DUTY = 4095;
    public static final String PICS_DIRECTORY = "/home/pi/Pictures";

    private final I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
    private final I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("PCA9685").bus(I2C_BUS).device(PCA9685_ADDR).build();
    private final I2C pca9685;

    private static final Sinks.Many<byte[]> SINK = Sinks.many()
        .multicast()
        .onBackpressureBuffer(2000, false);

    RpiCamStill photoCamera = new RpiCamStill()
        .setOutputDir(PICS_DIRECTORY)
        .setDimensions(600, 800)
        .setTimeout(100)
        .setEncoding("jpg")
        .setQuality(95)
        .setVerbose(false);

    private final RpiCamVid videoCamera = new RpiCamVid()
        .setDimensions(600, 600)
        .setTimeout(Integer.MAX_VALUE)
        .setEncoding("mjpeg")
        .setFramerate(30)
        .setVerbose(false);

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!RpiCamVid.isAvailable()) {
            System.err.println("rpicam-vid not available or unsupported hardware version.");
            return;
        }
        Schedulers.boundedElastic()
            .schedule(() -> {
                try (InputStream videoStream = videoCamera.streamVideo()) {
                    byte[] buffer = new byte[1024 * 64];
                    ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                    int bytesRead;
                    boolean inFrame = false;
                    while ((bytesRead = videoStream.read(buffer)) != -1) {
                        for (int i = 0; i < bytesRead; i++) {
                            if (!inFrame && buffer[i] == (byte) 0xFF && i + 1 < bytesRead && buffer[i + 1] == (byte) 0xD8) {
                                frameBuffer.reset();
                                inFrame = true;
                            }
                            if (inFrame) {
                                frameBuffer.write(buffer[i]);
                                if (buffer[i] == (byte) 0xFF && i + 1 < bytesRead && buffer[i + 1] == (byte) 0xD9) {
                                    frameBuffer.write(buffer[i + 1]);
                                    i++;
                                    inFrame = false;
                                    byte[] imageBytes = frameBuffer.toByteArray();
                                    String header = "--frame\r\nContent-Type: image/jpeg\r\n\r\n";

                                    SINK.tryEmitNext(header.getBytes());
                                    SINK.tryEmitNext(imageBytes);
                                    SINK.tryEmitNext ("\r\n".getBytes());

                                    frameBuffer.reset();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Video stream error: " + e.getMessage());
                    SINK.tryEmitComplete();
                }
            });
    }

    public Application() throws InterruptedException {
        I2C pca9685 = i2CProvider.create(i2cConfig);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10);
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1);
        this.pca9685 = pca9685;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    @GetMapping(value = "v3/video-stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public Flux<byte[]> streamVideoMjpegV3() {
        return SINK.asFlux();
    }

    @GetMapping(value = "/image")
    public Mono<String> getCameraImage() {
        return photoCamera.captureStillAsync(UUID.randomUUID() + ".jpg")
            .map(file -> "http://192.168.1.165:8080/files/" + file.getName());
    }

    @GetMapping("/move")
    public String move(@RequestParam String command) {
        MovementCommand movement;
        movement = MovementCommand.valueOf(command.trim().toUpperCase());
        int[] duties = getDutiesForCommand(movement);
        Mono.fromRunnable(() -> {
            setMotorModel(duties[0], duties[1], duties[2], duties[3]);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            setMotorModel(0, 0, 0, 0);
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
        return "Moved " + movement.name().toLowerCase();
    }

    private int[] getDutiesForCommand(MovementCommand command) {
        return switch (command) {
            case FORWARD -> new int[]{-1400, -1400, -1400, -1400};   // Use negative for forward
            case BACKWARD -> new int[]{1400, 1400, 1400, 1400};      // Use positive for backward
            case RIGHT -> new int[]{-1400, -1400, 1400, 1400};
            case LEFT -> new int[]{1400, 1400, -1400, -1400};
        };
    }

    // Sets all four wheels using the same logic as the Python code
    private void setMotorModel(int duty1, int duty2, int duty3, int duty4) {
        duty1 = clampDuty(duty1);
        duty2 = clampDuty(duty2);
        duty3 = clampDuty(duty3);
        duty4 = clampDuty(duty4);
        leftUpperWheel(duty1);
        leftLowerWheel(duty2);
        rightUpperWheel(duty3);
        rightLowerWheel(duty4);
    }

    private int clampDuty(int duty) {
        if (duty > MAX_DUTY) return MAX_DUTY;
        if (duty < -MAX_DUTY) return -MAX_DUTY;
        return duty;
    }

    // Each wheel uses two channels, set according to duty sign
    private void leftUpperWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(0, 0);
            setMotorPwm(1, duty);
        } else if (duty < 0) {
            setMotorPwm(1, 0);
            setMotorPwm(0, Math.abs(duty));
        } else {
            setMotorPwm(0, MAX_DUTY);
            setMotorPwm(1, MAX_DUTY);
        }
    }

    private void leftLowerWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(3, 0);
            setMotorPwm(2, duty);
        } else if (duty < 0) {
            setMotorPwm(2, 0);
            setMotorPwm(3, Math.abs(duty));
        } else {
            setMotorPwm(2, MAX_DUTY);
            setMotorPwm(3, MAX_DUTY);
        }
    }

    private void rightUpperWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(6, 0);
            setMotorPwm(7, duty);
        } else if (duty < 0) {
            setMotorPwm(7, 0);
            setMotorPwm(6, Math.abs(duty));
        } else {
            setMotorPwm(6, MAX_DUTY);
            setMotorPwm(7, MAX_DUTY);
        }
    }

    private void rightLowerWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(4, 0);
            setMotorPwm(5, duty);
        } else if (duty < 0) {
            setMotorPwm(5, 0);
            setMotorPwm(4, Math.abs(duty));
        } else {
            setMotorPwm(4, MAX_DUTY);
            setMotorPwm(5, MAX_DUTY);
        }
    }

    // Set PWM for a single channel
    private void setMotorPwm(int channel, int value) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) 0); // ON time low byte
        pca9685.writeRegister(reg + 1, (byte) 0); // ON time high byte
        pca9685.writeRegister(reg + 2, (byte) (value & 0xFF)); // OFF time low byte
        pca9685.writeRegister(reg + 3, (byte) ((value >> 8) & 0xFF)); // OFF time high byte
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT;
    }
}
