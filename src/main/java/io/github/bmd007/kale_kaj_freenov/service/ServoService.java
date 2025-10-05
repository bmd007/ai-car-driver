package io.github.bmd007.kale_kaj_freenov.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ServoService {
    private static final int INITIAL_PULSE = 1500;
    private final Map<String, Integer> pwmChannelMap;
    private final PCA9685 pca9685;

    public ServoService(PCA9685 pca9685) {
        this.pca9685 = pca9685;
        this.pwmChannelMap = new HashMap<>();
        pwmChannelMap.put("0", 8);
        pwmChannelMap.put("1", 9);
        pwmChannelMap.put("2", 10);
        pwmChannelMap.put("3", 11);
        pwmChannelMap.put("4", 12);
        pwmChannelMap.put("5", 13);
        pwmChannelMap.put("6", 14);
        pwmChannelMap.put("7", 15);

        // Set initial pulse for all servos
        for (int channel : pwmChannelMap.values()) {
            setServoPulse(channel, INITIAL_PULSE);
        }
    }

    public void setServoPwm(String channel, int angle) {
        int error = 10;
        if (!pwmChannelMap.containsKey(channel)) {
            throw new IllegalArgumentException("Invalid channel: " + channel + ". Valid channels are " + pwmChannelMap.keySet());
        }
        int pulse;
        if ("0".equals(channel)) {
            pulse = 2500 - (int) ((angle + error) / 0.09);
        } else {
            pulse = 500 + (int) ((angle + error) / 0.09);
        }
        setServoPulse(pwmChannelMap.get(channel), pulse);
    }

    private void setServoPulse(int channel, int pulse) {
        // PWM frequency is 50Hz, period is 20000us
        int pwmValue = (int) (pulse * 4096 / 20000.0);
        pca9685.setPwm(channel, 0, pwmValue);
    }
}
