package io.github.bmd007.kale_kaj_freenov;

import io.github.bmd007.kale_kaj_freenov.service.MotorService;
import io.github.bmd007.kale_kaj_freenov.service.RpiCamVid;
import io.github.bmd007.kale_kaj_freenov.service.ServoService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;


@RestController
@SpringBootApplication
public class ActuatorResource {

    private static final Sinks.Many<byte[]> SINK = Sinks.many()
        .multicast()
        .onBackpressureBuffer(4, false);
    private static final RpiCamVid VIDEO_CAMERA = new RpiCamVid()
        .setDimensions(600, 600)
        .setTimeout(Integer.MAX_VALUE)
        .setEncoding("mjpeg")
        .setFramerate(30)
        .setVerbose(false);

    private final MotorService motorService;
    private final ServoService servoService;

    public ActuatorResource(MotorService motorService, ServoService servoService) {
        this.motorService = motorService;
        this.servoService = servoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!RpiCamVid.isAvailable()) {
            System.err.println("rpicam-vid not available or unsupported hardware version.");
            return;
        }
        Schedulers.boundedElastic()
            .schedule(() -> {
                try (InputStream videoStream = VIDEO_CAMERA.streamVideo()) {
                    byte[] buffer = new byte[1024 * 64];
                    ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                    int bytesRead;
                    boolean inFrame = false;
                    while ((bytesRead = videoStream.read(buffer)) != -1) {
                        for (int i = 0; i < bytesRead; i++) {
                            if (!inFrame && buffer[i] == (byte) 0xFF && i + 1 < bytesRead && buffer[i + 1] == (byte) 0xD8) {
                                frameBuffer.reset();
                                inFrame = true;
                            }
                            if (inFrame) {
                                frameBuffer.write(buffer[i]);
                                if (buffer[i] == (byte) 0xFF && i + 1 < bytesRead && buffer[i + 1] == (byte) 0xD9) {
                                    frameBuffer.write(buffer[i + 1]);
                                    i++;
                                    inFrame = false;
                                    byte[] imageBytes = frameBuffer.toByteArray();
                                    String header = "--frame\r\nContent-Type: image/jpeg\r\n\r\n";

                                    SINK.tryEmitNext(header.getBytes());
                                    SINK.tryEmitNext(imageBytes);
                                    SINK.tryEmitNext("\r\n".getBytes());

                                    frameBuffer.reset();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Video stream error: " + e.getMessage());
                    //todo report the error to telegram
//                    SINK.tryEmitComplete();
                }
            });
    }

    @GetMapping(value = "v3/video-stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public Flux<byte[]> videoStream() {
        return SINK.asFlux();
    }

    @PostMapping("/move")
    public void move(@RequestParam String command) {
        var movement = MotorService.MovementCommand.valueOf(command.trim().toUpperCase());
        motorService.move(movement);
    }

    @PostMapping("/rotate-head")
    public void rotateHead(@RequestParam String channel, @RequestParam int angle) {
        servoService.setServoPwm(channel, angle);
    }
}
