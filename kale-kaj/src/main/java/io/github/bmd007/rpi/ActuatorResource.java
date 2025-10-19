package io.github.bmd007.rpi;

import io.github.bmd007.rpi.service.MotorService;
import io.github.bmd007.rpi.service.RpiCamStill;
import io.github.bmd007.rpi.service.RpiCamVid;
import io.github.bmd007.rpi.service.ServoService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.bmd007.rpi.service.MotorService.MovementCommand;


@RestController
@SpringBootApplication
public class ActuatorResource {

    private static final Sinks.Many<byte[]> SINK = Sinks.many()
        .multicast()
        .onBackpressureBuffer(4, false);

    private static final AtomicReference<byte[]> LATEST_FRAME = new AtomicReference<>();

    private static final RpiCamVid VIDEO_CAMERA = new RpiCamVid()
        .setDimensions(640, 480) // Lower resolution = faster processing
        .setTimeout(Integer.MAX_VALUE)
        .setEncoding("mjpeg")
        .setFramerate(30)
        .setVerbose(false);

    //cant use still image camera when video camera is running
    private static final RpiCamStill IMAGE_CAMERA = new RpiCamStill()
        .setDimensions(640, 480) // Lower resolution = faster processing
        .setQuality(85) // 85 is fine, 100 is overkill
        .setTimeout(5)
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

                                    // Store the latest frame for capture-image endpoint
                                    LATEST_FRAME.set(imageBytes);

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

    @GetMapping(value = "v3/capture-image", produces = MediaType.IMAGE_JPEG_VALUE)
    public Mono<byte[]> captureImage() {
        return Mono.defer(() -> {
                byte[] frame = LATEST_FRAME.get();
                if (frame == null) {
                    return Mono.error(new IllegalStateException("No video frame available yet. Please wait for video stream to start."));
                }
                return Mono.just(frame);
            })
            .timeout(Duration.ofSeconds(5))
            .subscribeOn(Schedulers.boundedElastic());
    }

    //todo add rate limited, one request per second
    @PostMapping("move")
    public void move(@RequestParam String command) {
        var movement = MovementCommand.valueOf(command.trim().toUpperCase());
        motorService.move(movement);
    }

    //todo add rate limited, one request per second
    @PostMapping("rotate-head")
    public void rotateHead(@RequestParam String channel, @RequestParam int angle) {
        servoService.setServoPwm(channel, angle);
    }
}
