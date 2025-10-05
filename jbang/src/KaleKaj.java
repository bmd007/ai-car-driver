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
    private static final int LED0_ON_L = 0x06;
    private static final int PCA9685_ADDR = 0x40;

    private static final I2CProvider I2C_PROVIDER = pi4j.provider("linuxfs-i2c");
    private static final I2CConfig I2C_CONFIG = I2C.newConfigBuilder(pi4j)
        .id("PCA9685")
        .bus(I2C_BUS)
        .device(PCA9685_ADDR)
        .build();
    private final I2C pca9685;

    public static void main(String[] args) throws InterruptedException {
        var kaleKaj = new KaleKaj();
        for (int i = 0; i < 10000; i++) {
            kaleKaj.setServoAngle(4, i % 180);
            Thread.sleep(50);
            kaleKaj.setServoAngle(5, 180 - (i % 180));
            Thread.sleep(1000);
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
        int pulse = angleToPulse(angle);
        setServoPulse(channel, pulse);
    }

    private int angleToPulse(int angle) {
        int pulseUs = 500 + (int) ((angle / 180.0) * (2500 - 500));
        return (pulseUs * 4096) / 20000; // 20ms period for 50Hz
    }

    private void setServoPulse(int channel, int pulse) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) 0);
        pca9685.writeRegister(reg + 1, (byte) 0);
        pca9685.writeRegister(reg + 2, (byte) (pulse & 0xFF));
        pca9685.writeRegister(reg + 3, (byte) ((pulse >> 8) & 0xFF));
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT
    }
}
