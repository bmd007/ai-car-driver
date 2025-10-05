package io.github.bmd007.kale_kaj_freenov.service;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MotorService {

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final int I2C_BUS = 1;
    private static final int MODE1 = 0x00;
    private static final int PWM_FREQ = 50;
    private static final int MAX_DUTY = 4095;
    private static final int PRESCALE = 0xFE;
    private static final int LED0_ON_L = 0x06;
    private static final int PCA9685_ADDR = 0x40;

    private static final int[] SERVO_CHANNELS = {4, 5}; // Servo 0 and 1

    private static final I2CProvider I2C_PROVIDER = pi4j.provider("linuxfs-i2c");
    private static final I2CConfig I2C_CONFIG = I2C.newConfigBuilder(pi4j)
        .id("PCA9685")
        .bus(I2C_BUS)
        .device(PCA9685_ADDR)
        .build();
    private final I2C pca9685;

    public MotorService() throws InterruptedException {
        this.pca9685 = I2C_PROVIDER.create(I2C_CONFIG);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10);
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1);

        // Initialize servos to 90 degrees
        for (int channel : SERVO_CHANNELS) {
            setServoPulse(channel, angleToPulse(90));
        }
    }

    // --- Motor control methods

    public void move(MovementCommand command) {
        int[] duties = getDutiesForCommand(command);
        Mono.fromRunnable(() -> {
                setMotorModel(duties[0], duties[1], duties[2], duties[3]);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                setMotorModel(0, 0, 0, 0);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private int[] getDutiesForCommand(MovementCommand command) {
        return switch (command) {
            case FORWARD -> new int[]{-1400, -1400, -1400, -1400};
            case BACKWARD -> new int[]{1400, 1400, 1400, 1400};
            case RIGHT -> new int[]{-1400, -1400, 1400, 1400};
            case LEFT -> new int[]{1400, 1400, -1400, -1400};
        };
    }

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

    private void setMotorPwm(int channel, int value) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) 0);
        pca9685.writeRegister(reg + 1, (byte) 0);
        pca9685.writeRegister(reg + 2, (byte) (value & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((value >> 8) & 0xFF));
    }

    // --- Servo control methods ---

    /**
     * Set servo angle for a given channel.
     *
     * @param channel Servo channel (0 for servo 0, 1 for servo 1)
     * @param angle   Angle in degrees (0-180)
     */
    public void setServoAngle(int channel, int angle) {
        if (channel < 0 || channel >= SERVO_CHANNELS.length) {
            throw new IllegalArgumentException("Invalid servo channel: " + channel);
        }
        int pulse = angleToPulse(angle);
        setServoPulse(SERVO_CHANNELS[channel], pulse);
    }

    // Convert angle (0-180) to pulse width (500-2500us)
    private int angleToPulse(int angle) {
        int pulseUs = 500 + (int) ((angle / 180.0) * (2500 - 500));
        return (pulseUs * 4096) / 20000; // 20ms period for 50Hz
    }

    // Set PWM pulse for a servo channel
    private void setServoPulse(int channel, int pulse) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) 0);
        pca9685.writeRegister(reg + 1, (byte) 0);
        pca9685.writeRegister(reg + 2, (byte) (pulse & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((pulse >> 8) & 0xFF));
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT;
    }
}
