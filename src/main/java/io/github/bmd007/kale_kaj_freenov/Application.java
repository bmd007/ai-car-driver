// src/main/java/io/github/bmd007/kale_kaj_freenov/Application.java
package io.github.bmd007.kale_kaj_freenov;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import com.pi4j.util.Console;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class Application {

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final Console console = new Console();
    private static final int PCA9685_ADDR = 0x40;
    private static final int I2C_BUS = 1;
    private static final int MODE1 = 0x00;
    private static final int PRESCALE = 0xFE;
    private static final int LED0_ON_L = 0x06;
    private static final int PWM_FREQ = 50;

    private final I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
    private final I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j)
        .id("PCA9685")
        .bus(I2C_BUS)
        .device(PCA9685_ADDR)
        .build();
    private final I2C pca9685;

    public Application() throws Exception {
        I2C pca9685 = i2CProvider.create(i2cConfig);
        pca9685.writeRegister(MODE1, (byte) 0x00); // Wake up
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10); // Sleep
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00); // Wake
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1); // Auto-increment
        this.pca9685 = pca9685;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {

    }

    @GetMapping("/move")
    public ResponseEntity<String> move(@RequestParam String command) {
        var movement = MovementCommand.fromString(command);
        if (movement == null) {
            return ResponseEntity.badRequest().body("Invalid command");
        }
        try (pca9685) {
            int[] pwm = getPwmForCommand(movement);
            for (int i = 0; i < 4; i++) {
                setPwm(pca9685, i, 0, pwm[i]);
            }
            Thread.sleep(4000);
            for (int i = 0; i < 4; i++) {
                setPwm(pca9685, i, 0, 0);
            }
            return ResponseEntity.ok("Moved " + movement.name().toLowerCase());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hardware error: " + e.getMessage());
        }
    }

    private void setPwm(I2C pca9685, int channel, int on, int off) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) (on & 0xFF));
        pca9685.writeRegister(reg + 1, (byte) ((on >> 8) & 0xFF));
        pca9685.writeRegister(reg + 2, (byte) (off & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((off >> 8) & 0xFF));
    }

    private int[] getPwmForCommand(MovementCommand command) {
        return switch (command) {
            case FORWARD -> new int[]{2000, 2000, 2000, 2000};
            case BACKWARD -> new int[]{-2000, -2000, -2000, -2000};
            case LEFT -> new int[]{-2000, -2000, 2000, 2000};
            case RIGHT -> new int[]{2000, 2000, -2000, -2000};
        };
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT;

        public static MovementCommand fromString(String command) {
            if (command == null) return null;
            try {
                return MovementCommand.valueOf(command.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
