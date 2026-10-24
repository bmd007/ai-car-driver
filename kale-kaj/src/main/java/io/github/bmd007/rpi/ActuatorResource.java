package io.github.bmd007.rpi;

import io.github.bmd007.rpi.service.H264AccessUnitReader;
import io.github.bmd007.rpi.service.H264AccessUnitReader.AccessUnit;
import io.github.bmd007.rpi.service.KeyframeToJpeg;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.bmd007.rpi.service.MotorService.MovementCommand;

@RestController
@SpringBootApplication
public class ActuatorResource {

    private static final Duration CAMERA_RESTART_DELAY = Duration.ofSeconds(2);
    private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(2);

    private static final Sinks.Many<AccessUnit> SINK = Sinks.many()
        .multicast()
        .directBestEffort();

    private static final AtomicReference<AccessUnit> LATEST_KEYFRAME = new AtomicReference<>();
    private static final AtomicReference<Snapshot> LATEST_SNAPSHOT = new AtomicReference<>();
    private static final AtomicReference<InputStream> CURRENT_STREAM = new AtomicReference<>();
    private static final AtomicLong LAST_FRAME_AT = new AtomicLong();

    private static final RpiCamVid VIDEO_CAMERA = new RpiCamVid()
        .setDimensions(640, 480)
        .setTimeout(0)
        .setEncoding("h264")
        .setFramerate(15)
        .setBitrate(700_000)
        .setVerbose(false);

    private record Snapshot(AccessUnit keyframe, byte[] jpeg) {
    }

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
                new H264AccessUnitReader(ActuatorResource::publish).read(videoStream);
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

    private static void publish(AccessUnit unit) {
        LAST_FRAME_AT.set(System.nanoTime());
        if (unit.keyframe()) {
            LATEST_KEYFRAME.set(unit);
        }
        SINK.tryEmitNext(unit);
    }

    public static Flux<byte[]> h264Stream() {
        var waitingForKeyframe = new AtomicBoolean(true);
        return SINK.asFlux()
            .onBackpressureDrop(_ -> waitingForKeyframe.set(true))
            .filter(unit -> {
                if (unit.keyframe()) {
                    waitingForKeyframe.set(false);
                }
                return !waitingForKeyframe.get();
            })
            .map(AccessUnit::data);
    }

    private static synchronized byte[] latestJpeg() {
        var keyframe = LATEST_KEYFRAME.get();
        if (keyframe == null) {
            throw new IllegalStateException("No video frame available yet. Please wait for video stream to start.");
        }
        var snapshot = LATEST_SNAPSHOT.get();
        if (snapshot != null && snapshot.keyframe() == keyframe) {
            return snapshot.jpeg();
        }
        try {
            var jpeg = KeyframeToJpeg.convert(keyframe.data());
            LATEST_SNAPSHOT.set(new Snapshot(keyframe, jpeg));
            return jpeg;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping(value = "v3/capture-image", produces = MediaType.IMAGE_JPEG_VALUE)
    public Mono<byte[]> captureImage() {
        return Mono.fromCallable(ActuatorResource::latestJpeg)
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
