package io.github.bmd007.kale_kaj_freenov;

import com.hopding.jrpicam.RPiCamera;
import com.hopding.jrpicam.enums.Exposure;
import com.hopding.jrpicam.exceptions.FailedToRunRaspistillException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;


@RestController
@SpringBootApplication
public class Application {

    private static final Context pi4j = Pi4J.newAutoContext();
    private static final int PCA9685_ADDR = 0x40;
    private static final int I2C_BUS = 1;
    private static final int MODE1 = 0x00;
    private static final int PRESCALE = 0xFE;
    private static final int PWM_FREQ = 50;
    private static final int LED0_ON_L = 0x06;
    private static final int MAX_DUTY = 4095;

    private final I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
    private final I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("PCA9685").bus(I2C_BUS).device(PCA9685_ADDR).build();
    private final I2C pca9685;
    RPiCamera piCamera = new RPiCamera("/home/pi/freenov-kale-kaj/tmp");

    public Application() throws InterruptedException, FailedToRunRaspistillException {
        I2C pca9685 = i2CProvider.create(i2cConfig);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        int prescale = (int) Math.round(25000000.0 / (4096 * PWM_FREQ) - 1);
        pca9685.writeRegister(MODE1, (byte) 0x10);
        pca9685.writeRegister(PRESCALE, (byte) prescale);
        pca9685.writeRegister(MODE1, (byte) 0x00);
        Thread.sleep(1);
        pca9685.writeRegister(MODE1, (byte) 0xA1);
        this.pca9685 = pca9685;

        piCamera.setWidth(500).setHeight(500) // Set Camera to produce 500x500 images.
            .setBrightness(75)                // Adjust Camera's brightness setting.
            .setExposure(Exposure.AUTO)       // Set Camera's exposure.
            .setTimeout(2)                    // Set Camera's timeout.
            .setAddRawBayer(true);            // Add Raw Bayer data to image files created by Camera.
// Sets all Camera options to their default settings, overriding any changes previously made.
        piCamera.setToDefaults();
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }

    @GetMapping(value = "/camera/{fileName}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> getCameraImage(@PathVariable String fileName) throws IOException, InterruptedException {
        var file = piCamera.takeStill(fileName);
        var resource = new FileSystemResource(file);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String disposition = "attachment";
        try {
            String detectedType = Files.probeContentType(file.toPath());
            if (detectedType != null) {
                contentType = detectedType;
                if (contentType.startsWith("image/") || contentType.startsWith("video/")) {
                    disposition = "inline";
                }
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + file.getName() + "\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(resource);
    }

    @GetMapping("/move")
    public ResponseEntity<String> move(@RequestParam String command) {
        MovementCommand movement;
        try {
            movement = MovementCommand.valueOf(command.trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid command");
        }
        int[] duties = getDutiesForCommand(movement);
        setMotorModel(duties[0], duties[1], duties[2], duties[3]);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException a) {
        }
        setMotorModel(0, 0, 0, 0);
        return ResponseEntity.ok("Moved " + movement.name().toLowerCase());
    }

    // Maps movement command to duty values for each wheel
    private int[] getDutiesForCommand(MovementCommand command) {
        return switch (command) {
            case FORWARD -> new int[]{-2000, -2000, -2000, -2000};   // Use negative for forward
            case BACKWARD -> new int[]{2000, 2000, 2000, 2000};      // Use positive for backward
            case RIGHT -> new int[]{-2000, -2000, 2000, 2000};
            case LEFT -> new int[]{2000, 2000, -2000, -2000};
        };
    }

    // Sets all four wheels using the same logic as the Python code
    private void setMotorModel(int duty1, int duty2, int duty3, int duty4) {
        duty1 = clampDuty(duty1);
        duty2 = clampDuty(duty2);
        duty3 = clampDuty(duty3);
        duty4 = clampDuty(duty4);
        leftUpperWheel(duty1);
        leftLowerWheel(duty2);
        rightUpperWheel(duty3);
        rightLowerWheel(duty4);
    }

    private int clampDuty(int duty) {
        if (duty > MAX_DUTY) return MAX_DUTY;
        if (duty < -MAX_DUTY) return -MAX_DUTY;
        return duty;
    }

    // Each wheel uses two channels, set according to duty sign
    private void leftUpperWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(0, 0);
            setMotorPwm(1, duty);
        } else if (duty < 0) {
            setMotorPwm(1, 0);
            setMotorPwm(0, Math.abs(duty));
        } else {
            setMotorPwm(0, MAX_DUTY);
            setMotorPwm(1, MAX_DUTY);
        }
    }

    private void leftLowerWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(3, 0);
            setMotorPwm(2, duty);
        } else if (duty < 0) {
            setMotorPwm(2, 0);
            setMotorPwm(3, Math.abs(duty));
        } else {
            setMotorPwm(2, MAX_DUTY);
            setMotorPwm(3, MAX_DUTY);
        }
    }

    private void rightUpperWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(6, 0);
            setMotorPwm(7, duty);
        } else if (duty < 0) {
            setMotorPwm(7, 0);
            setMotorPwm(6, Math.abs(duty));
        } else {
            setMotorPwm(6, MAX_DUTY);
            setMotorPwm(7, MAX_DUTY);
        }
    }

    private void rightLowerWheel(int duty) {
        if (duty > 0) {
            setMotorPwm(4, 0);
            setMotorPwm(5, duty);
        } else if (duty < 0) {
            setMotorPwm(5, 0);
            setMotorPwm(4, Math.abs(duty));
        } else {
            setMotorPwm(4, MAX_DUTY);
            setMotorPwm(5, MAX_DUTY);
        }
    }

    // Set PWM for a single channel
    private void setMotorPwm(int channel, int value) {
        int reg = LED0_ON_L + 4 * channel;
        pca9685.writeRegister(reg, (byte) 0); // ON time low byte
        pca9685.writeRegister(reg + 1, (byte) 0); // ON time high byte
        pca9685.writeRegister(reg + 2, (byte) (value & 0xFF)); // OFF time low byte
        pca9685.writeRegister(reg + 3, (byte) ((value >> 8) & 0xFF)); // OFF time high byte
    }

    public enum MovementCommand {
        FORWARD, BACKWARD, LEFT, RIGHT;
    }
}
