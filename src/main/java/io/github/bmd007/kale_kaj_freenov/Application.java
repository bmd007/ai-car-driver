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

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        console.title("<-- The Pi4J Project -->");
        try (I2C pca9685 = initializePCA9685()) {
            moveMotor(pca9685, 0, 2000, 1000); // Move left front motor forward for 1s
        } catch (Exception e) {
            console.box("Hardware initialization failed: " + e.getMessage());
        }
    }

    @GetMapping("/move")
    public ResponseEntity<String> move(@RequestParam String command) {
        try (I2C pca9685 = initializePCA9685()) {
            int[] pwm = getPwmForCommand(command);
            if (pwm == null) {
                return ResponseEntity.badRequest().body("Invalid command");
            }
            for (int i = 0; i < 4; i++) {
                setPwm(pca9685, i, 0, pwm[i]);
            }
            Thread.sleep(1000);
            for (int i = 0; i < 4; i++) {
                setPwm(pca9685, i, 0, 0);
            }
            return ResponseEntity.ok("Moved " + command);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Hardware error: " + e.getMessage());
        }
    }

    /**
     * Initializes the PCA9685 PWM controller and sets the frequency.
     *
     * @return I2C instance for PCA9685
     * @throws Exception on hardware error
     */
    private I2C initializePCA9685() throws Exception {
        I2C pca9685 = i2CProvider.create(i2cConfig);
        pca9685.writeRegister(MODE1, (byte) 0x00); // Wake up
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10); // Sleep
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00); // Wake
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1); // Auto-increment
        return pca9685;
    }

    /**
     * Sets PWM for a specific channel.
     *
     * @param pca9685 I2C instance
     * @param channel Motor channel (0-3)
     * @param on      PWM ON time
     * @param off     PWM OFF time
     */
    private void setPwm(I2C pca9685, int channel, int on, int off) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) (on & 0xFF));
        pca9685.writeRegister(reg + 1, (byte) ((on >> 8) & 0xFF));
        pca9685.writeRegister(reg + 2, (byte) (off & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((off >> 8) & 0xFF));
    }

    /**
     * Moves a single motor for a specified duration.
     *
     * @param pca9685    I2C instance
     * @param channel    Motor channel
     * @param speed      PWM value
     * @param durationMs Duration in milliseconds
     * @throws InterruptedException if sleep fails
     */
    private void moveMotor(I2C pca9685, int channel, int speed, int durationMs) throws InterruptedException {
        setPwm(pca9685, channel, 0, speed);
        Thread.sleep(durationMs);
        setPwm(pca9685, channel, 0, 0);
    }

    /**
     * Returns PWM values for motors based on movement command.
     *
     * @param command Movement command
     * @return Array of PWM values for channels 0-3, or null if invalid
     */
    private int[] getPwmForCommand(String command) {
        return switch (command.toLowerCase()) {
            case "forward" -> new int[]{2000, 2000, 2000, 2000};
            case "backward" -> new int[]{-2000, -2000, -2000, -2000};
            case "left" -> new int[]{-2000, -2000, 2000, 2000};
            case "right" -> new int[]{2000, 2000, -2000, -2000};
            default -> null;
        };
    }
}
