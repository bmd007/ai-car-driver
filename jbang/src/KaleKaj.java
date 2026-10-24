///usr/bin/env jbang "$0" "$@" ; exit $?
//MAIN KaleKaj
//DEPS com.pi4j:pi4j-core:4.0.2
//DEPS com.pi4j:pi4j-plugin-ffm:4.0.2
//SOURCES PCA9685.java
//JAVA 25+
//JAVA_OPTIONS --enable-native-access=ALL-UNNAMED

import com.pi4j.Pi4J;

import java.util.Map;

public class KaleKaj {

    private static final int I2C_BUS = 1;
    private static final int PCA9685_ADDR = 0x40;
    private static final int NEUTRAL_PULSE_US = 1500;
    private static final int SERVO_ERROR = 10;
    private static final Map<Integer, Integer> SERVO_CHANNELS = Map.of(
        0, 8,
        1, 9,
        2, 10,
        3, 11,
        4, 12,
        5, 13,
        6, 14,
        7, 15
    );

    private final PCA9685 pca9685;

    public KaleKaj(PCA9685 pca9685) {
        this.pca9685 = pca9685;
        SERVO_CHANNELS.values()
            .forEach(channel -> setServoPulse(channel, NEUTRAL_PULSE_US));
    }

    public static void main(String[] args) throws InterruptedException {
        var pi4j = Pi4J.newAutoContext();
        try {
            var kaleKaj = new KaleKaj(new PCA9685(pi4j, I2C_BUS, PCA9685_ADDR));
            var angle0 = args.length > 0 ? Integer.parseInt(args[0]) : 90;
            var angle1 = args.length > 1 ? Integer.parseInt(args[1]) : 90;
            System.out.println("Setting servo 0 to angle: " + angle0);
            System.out.println("Setting servo 1 to angle: " + angle1);
            kaleKaj.setServoAngle(0, angle0);
            kaleKaj.setServoAngle(1, angle1);
        } finally {
            pi4j.shutdown();
        }
    }

    public void setServoAngle(int servo, int angle) {
        var channel = SERVO_CHANNELS.get(servo);
        if (channel == null) {
            throw new IllegalArgumentException("Invalid servo: " + servo + ". Valid servos are " + SERVO_CHANNELS.keySet());
        }
        var offset = (int) ((angle + SERVO_ERROR) / 0.09);
        var pulse = servo == 0 ? 2500 - offset : 500 + offset;
        setServoPulse(channel, Math.clamp(pulse, 500, 2500));
    }

    private void setServoPulse(int channel, int pulseUs) {
        var ticks = pulseUs * 4096 / 20000;
        pca9685.setPwm(channel, 0, ticks);
    }
}
