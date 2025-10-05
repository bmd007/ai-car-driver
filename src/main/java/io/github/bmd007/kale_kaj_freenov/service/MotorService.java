package io.github.bmd007.kale_kaj_freenov.service;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import io.github.bmd007.kale_kaj_freenov.service.PCA9685;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MotorService {

    private static final int I2C_BUS = 1;
    private static final int PCA9685_ADDR = 0x40;
    private static final int MAX_DUTY = 4095;
    private final PCA9685 pca9685;

    public MotorService() throws InterruptedException {
        Context pi4j = Pi4J.newAutoContext();
        this.pca9685 = new PCA9685(pi4j, I2C_BUS, PCA9685_ADDR);
    }

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
            case RIGHT -> new int[]{-1400, -1400, 1600, 1600};
            case LEFT -> new int[]{1600, 1600, -1400, -1400};
        };
    }

    private void setMotorModel(int duty1, int duty2, int duty3, int duty4) {
        leftUpperWheel(duty1);
        leftLowerWheel(duty2);
        rightUpperWheel(duty3);
        rightLowerWheel(duty4);
    }

    private void leftUpperWheel(int duty) {
        if (duty > 0) {
            pca9685.setMotorPwm(0, 0);
            pca9685.setMotorPwm(1, duty);
        } else if (duty < 0) {
            pca9685.setMotorPwm(1, 0);
            pca9685.setMotorPwm(0, Math.abs(duty));
        } else {
            pca9685.setMotorPwm(0, MAX_DUTY);
            pca9685.setMotorPwm(1, MAX_DUTY);
        }
    }

    private void leftLowerWheel(int duty) {
        if (duty > 0) {
            pca9685.setMotorPwm(3, 0);
            pca9685.setMotorPwm(2, duty);
        } else if (duty < 0) {
            pca9685.setMotorPwm(2, 0);
            pca9685.setMotorPwm(3, Math.abs(duty));
        } else {
            pca9685.setMotorPwm(2, MAX_DUTY);
            pca9685.setMotorPwm(3, MAX_DUTY);
        }
    }

    private void rightUpperWheel(int duty) {
        if (duty > 0) {
            pca9685.setMotorPwm(6, 0);
            pca9685.setMotorPwm(7, duty);
        } else if (duty < 0) {
            pca9685.setMotorPwm(7, 0);
            pca9685.setMotorPwm(6, Math.abs(duty));
        } else {
            pca9685.setMotorPwm(6, MAX_DUTY);
            pca9685.setMotorPwm(7, MAX_DUTY);
        }
    }

    private void rightLowerWheel(int duty) {
        if (duty > 0) {
            pca9685.setMotorPwm(4, 0);
            pca9685.setMotorPwm(5, duty);
        } else if (duty < 0) {
            pca9685.setMotorPwm(5, 0);
            pca9685.setMotorPwm(4, Math.abs(duty));
        } else {
            pca9685.setMotorPwm(4, MAX_DUTY);
            pca9685.setMotorPwm(5, MAX_DUTY);
        }
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT
    }
}
