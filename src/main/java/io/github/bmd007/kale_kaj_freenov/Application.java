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

@SpringBootApplication
public class Application {

    static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final Console console = new Console();

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        console.title("<-- The Pi4J Project -->");
        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("PCA9685").bus(1).device(0x40).build();

        // PCA9685 register addresses
        final int MODE1 = 0x00;
        final int PRESCALE = 0xFE;
        final int LED0_ON_L = 0x06;

        try (I2C pca9685 = i2CProvider.create(i2cConfig)) {
            // Wake up PCA9685
            pca9685.writeRegister(MODE1, (byte) 0x00);

            // Set PWM frequency to 50Hz
            int freq = 50;
            int prescale = (int) Math.round(25000000.0 / (4096 * freq) - 1);
            pca9685.writeRegister(MODE1, (byte) 0x10); // sleep
            pca9685.writeRegister(PRESCALE, (byte) prescale);
            pca9685.writeRegister(MODE1, (byte) 0x00); // wake
            Thread.sleep(1);
            pca9685.writeRegister(MODE1, (byte) 0xA1); // auto-increment

            // Move left front motor (channel 0) forward (medium speed)
            setPwm(pca9685, 0, 0, 2000);
            Thread.sleep(1000); // run for 1 second

            // Stop motor
            setPwm(pca9685, 0, 0, 0);
        } catch (Exception e) {
            console.box("Hardware initialization failed: " + e.getMessage());
        }
    }

    // Helper to set PWM for a channel
    private void setPwm(I2C pca9685, int channel, int on, int off) {
        int reg = 0x06 + 4 * channel;
        pca9685.writeRegister(reg, (byte) (on & 0xFF));
        pca9685.writeRegister(reg + 1, (byte) ((on >> 8) & 0xFF));
        pca9685.writeRegister(reg + 2, (byte) (off & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((off >> 8) & 0xFF));
    }
}
