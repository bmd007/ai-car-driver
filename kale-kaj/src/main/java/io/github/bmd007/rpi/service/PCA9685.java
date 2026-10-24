package io.github.bmd007.rpi.service;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;

public class PCA9685 implements AutoCloseable {

    public static final int PWM_FREQ = 50;
    private static final int CHANNELS = 16;
    private static final int MODE1 = 0x00;
    private static final int PRESCALE = 0xFE;
    private static final int LED0_ON_L = 0x06;
    private static final int MAX_DUTY = 4095;
    private final I2C device;

    public PCA9685(Context pi4j, int bus, int address) throws InterruptedException {
        var config = I2C.newConfigBuilder(pi4j)
            .id("PCA9685")
            .bus(bus)
            .device(address)
            .build();
        this.device = pi4j.create(config);
        initialize(PWM_FREQ);
    }

    private void initialize(int freq) throws InterruptedException {
        device.writeRegister(MODE1, (byte) 0x00);
        var prescale = (int) Math.round(25000000.0 / (4096 * freq) - 1);
        device.writeRegister(MODE1, (byte) 0x10);
        device.writeRegister(PRESCALE, (byte) prescale);
        device.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        device.writeRegister(MODE1, (byte) 0xA1);
    }

    public void setPwm(int channel, int on, int off) {
        var data = new byte[]{
            (byte) (on & 0xFF),
            (byte) ((on >> 8) & 0xFF),
            (byte) (off & 0xFF),
            (byte) ((off >> 8) & 0xFF)
        };
        device.writeRegister(LED0_ON_L + 4 * channel, data, 0, data.length);
    }

    public void setMotorPwm(int channel, int duty) {
        setPwm(channel, 0, clampDuty(duty));
    }

    private int clampDuty(int duty) {
        if (duty > MAX_DUTY) return MAX_DUTY;
        if (duty < 0) return 0;
        return duty;
    }

    @Override
    public void close() {
        for (var channel = 0; channel < CHANNELS; channel++) {
            setPwm(channel, 0, 0);
        }
        device.close();
    }
}
