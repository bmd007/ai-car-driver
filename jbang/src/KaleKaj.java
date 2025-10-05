/// usr/bin/env jbang "$0" "$@" ; exit $?
///
//REPOS central, maven-central=https://repo.maven.apache.org/maven2/
//MAIN KaleKaj
//DEPS com.pi4j:pi4j-core:2.7.0
//DEPS com.pi4j:pi4j-plugin-gpiod:2.7.0
//DEPS com.pi4j:pi4j-plugin-linuxfs:2.7.0
//DEPS com.pi4j:pi4j-plugin-raspberrypi:2.7.0
//JAVA 17
///

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

public class KaleKaj {

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final int I2C_BUS = 1;
    private static final int MODE1 = 0x00;
    private static final int PWM_FREQ = 50;
    private static final int PRESCALE = 0xFE;
    private static final int PCA9685_ADDR = 0x40;
    private static final int SERVO_MIN_TICKS = 150;  // Adjust as needed
    private static final int SERVO_MAX_TICKS = 600;  // Adjust as needed

    private static final I2CProvider I2C_PROVIDER = pi4j.provider("linuxfs-i2c");
    private static final I2CConfig I2C_CONFIG = I2C.newConfigBuilder(pi4j)
        .id("PCA9685")
        .bus(I2C_BUS)
        .device(PCA9685_ADDR)
        .build();
    private final I2C pca9685;

    public static void main(String[] args) throws InterruptedException {
        var kaleKaj = new KaleKaj();
        var angle1 = args.length > 0 ? Integer.parseInt(args[0]) : 90;
        var angle2 = args.length > 1 ? Integer.parseInt(args[1]) : 90;
        System.out.println("Setting servo 1 to angle: " + angle1);
        System.out.println("Setting servo 2 to angle: " + angle2);
        kaleKaj.setServoAngle(4, angle1);
        kaleKaj.setServoAngle(5, angle2);

        kaleKaj.testServo();
    }
    public void testServo() throws InterruptedException {
        System.out.println("Testing servos...");
        // Sweep servo '0' (channel 4) from 50 to 110 and back
        for (int i = 50; i <= 110; i++) {
            setServoAngle(4, i);
            Thread.sleep(10);
        }
        for (int i = 110; i >= 50; i--) {
            setServoAngle(4, i);
            Thread.sleep(10);
        }
        // Sweep servo '1' (channel 5) from 80 to 150 and back
        for (int i = 80; i <= 150; i++) {
            setServoAngle(5, i);
            Thread.sleep(10);
        }
        for (int i = 150; i >= 80; i--) {
            setServoAngle(5, i);
            Thread.sleep(10);
        }
    }

    public KaleKaj() throws InterruptedException {
        this.pca9685 = I2C_PROVIDER.create(I2C_CONFIG);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10);
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1);
    }

    public void setServoAngle(int channel, int angle) {
        int error = 10;
        int pulse_us;
        // Match Python logic: channel 4 is '0', channel 5 is '1'
        if (channel == 4) {
            pulse_us = 2500 - (int) ((angle + error) / 0.09);
        } else {
            pulse_us = 500 + (int) ((angle + error) / 0.09);
        }
        int ticks = (pulse_us * 4096) / 20000; // 20ms period for 50Hz
        setServoPulse(channel, ticks);
    }

    private void setServoPulse(int channel, int ticks) {
        if (channel < 0 || channel > 15) {
            System.err.println("Invalid channel: " + channel);
            return;
        }
        int on = 0;
        int off = ticks;
        int base = 0x06 + 4 * channel;
        pca9685.writeRegister(base, (byte) (on & 0xFF));         // LEDn_ON_L
        pca9685.writeRegister(base + 1, (byte) (on >> 8));       // LEDn_ON_H
        pca9685.writeRegister(base + 2, (byte) (off & 0xFF));    // LEDn_OFF_L
        pca9685.writeRegister(base + 3, (byte) (off >> 8));      // LEDn_OFF_H
    }
}
