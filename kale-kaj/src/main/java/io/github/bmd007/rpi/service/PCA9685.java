package io.github.bmd007.rpi.service;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

public class PCA9685 {

    private static final int MODE1 = 0x00;
    private static final int PRESCALE = 0xFE;
    private static final int LED0_ON_L = 0x06;
    private static final int MAX_DUTY = 4095;
    private final I2C device;

    public PCA9685(Context pi4j, int bus, int address) throws InterruptedException {
        I2CProvider provider = pi4j.provider("linuxfs-i2c");
        I2CConfig config = I2C.newConfigBuilder(pi4j)
            .id("PCA9685")
            .bus(bus)
            .device(address)
            .build();
        this.device = provider.create(config);
        initialize(PWM_FREQ);
    }

    public static final int PWM_FREQ = 50;

    private void initialize(int freq) throws InterruptedException {
        device.writeRegister(MODE1, (byte) 0x00);
        int prescale = (int) Math.round(25000000.0 / (4096 * freq) - 1);
        device.writeRegister(MODE1, (byte) 0x10);
        device.writeRegister(PRESCALE, (byte) prescale);
        device.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        device.writeRegister(MODE1, (byte) 0xA1);
    }

    public void setPwm(int channel, int on, int off) {
        int reg = LED0_ON_L + 4 * channel;
        device.writeRegister(reg, (byte) (on & 0xFF));
        device.writeRegister(reg + 1, (byte) ((on >> 8) & 0xFF));
        device.writeRegister(reg + 2, (byte) (off & 0xFF));
        device.writeRegister(reg + 3, (byte) ((off >> 8) & 0xFF));
    }

    public void setMotorPwm(int channel, int duty) {
        setPwm(channel, 0, clampDuty(duty));
    }

    private int clampDuty(int duty) {
        if (duty > MAX_DUTY) return MAX_DUTY;
        if (duty < 0) return 0;
        return duty;
    }

    public void close() {
        device.close();
    }
}
