package io.github.bmd007.rpi;

import io.github.bmd007.rpi.service.MotorService;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.bmd007.rpi.service.MotorService.MovementCommand;

@RestController
@SpringBootApplication
public class ActuatorResource {

    private static final byte[] FRAME_HEADER = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FRAME_TRAILER = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final Duration CAMERA_RESTART_DELAY = Duration.ofSeconds(2);
    private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(2);

    private static final Sinks.Many<byte[]> SINK = Sinks.many()
        .multicast()
        .directBestEffort();

    private static final AtomicReference<byte[]> LATEST_FRAME = new AtomicReference<>();
    private static final AtomicReference<InputStream> CURRENT_STREAM = new AtomicReference<>();
    private static final AtomicLong LAST_FRAME_AT = new AtomicLong();

    private static final RpiCamVid VIDEO_CAMERA = new RpiCamVid()
        .setDimensions(640, 480)
        .setTimeout(0)
        .setEncoding("mjpeg")
        .setFramerate(15)
        .setVerbose(false);

    private final MotorService motorService;
    private final ServoService servoService;

    public ActuatorResource(MotorService motorService, ServoService servoService) {
        this.motorService = motorService;
        this.servoService = servoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Schedulers.boundedElastic()
            .schedule(ActuatorResource::streamFramesForever);
        Flux.interval(WATCHDOG_INTERVAL)
            .subscribe(tick -> restartStalledCamera());
    }

    private static void restartStalledCamera() {
        var stream = CURRENT_STREAM.get();
        var sinceLastFrame = Duration.ofNanos(System.nanoTime() - LAST_FRAME_AT.get());
        if (stream == null || sinceLastFrame.compareTo(FRAME_TIMEOUT) < 0) {
            return;
        }
        System.err.println("No video frame for " + sinceLastFrame.toSeconds() + "s, killing camera process.");
        try {
            stream.close();
        } catch (IOException e) {
            System.err.println("Failed to close stalled video stream: " + e.getMessage());
        }
    }

    private static void streamFramesForever() {
        while (!Thread.currentThread().isInterrupted()) {
            try (var videoStream = VIDEO_CAMERA.streamVideo()) {
                LAST_FRAME_AT.set(System.nanoTime());
                CURRENT_STREAM.set(videoStream);
                readFrames(videoStream);
                System.err.println("Video stream ended, restarting camera.");
            } catch (Exception e) {
                System.err.println("Video stream error, restarting camera: " + e.getMessage());
                //todo report the error to telegram
            } finally {
                CURRENT_STREAM.set(null);
            }
            try {
                Thread.sleep(CAMERA_RESTART_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void readFrames(InputStream videoStream) throws IOException {
        var buffer = new byte[64 * 1024];
        var frame = new ByteArrayOutputStream();
        var inFrame = false;
        var previous = 0;
        int bytesRead;
        while ((bytesRead = videoStream.read(buffer)) != -1) {
            for (var i = 0; i < bytesRead; i++) {
                var current = buffer[i] & 0xFF;
                if (previous == 0xFF && current == 0xD8) {
                    frame.reset();
                    frame.write(0xFF);
                    inFrame = true;
                }
                if (inFrame) {
                    frame.write(current);
                    if (previous == 0xFF && current == 0xD9) {
                        publish(frame.toByteArray());
                        inFrame = false;
                    }
                }
                previous = current;
            }
        }
    }

    private static void publish(byte[] image) {
        LAST_FRAME_AT.set(System.nanoTime());
        LATEST_FRAME.set(image);
        var part = new ByteArrayOutputStream(FRAME_HEADER.length + image.length + FRAME_TRAILER.length);
        part.writeBytes(FRAME_HEADER);
        part.writeBytes(image);
        part.writeBytes(FRAME_TRAILER);
        SINK.tryEmitNext(part.toByteArray());
    }

    @GetMapping(value = "v3/video-stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public Flux<byte[]> videoStream() {
        return SINK.asFlux()
            .onBackpressureLatest();
    }

    @GetMapping(value = "v3/capture-image", produces = MediaType.IMAGE_JPEG_VALUE)
    public Mono<byte[]> captureImage() {
        return Mono.defer(() -> {
                var frame = LATEST_FRAME.get();
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
