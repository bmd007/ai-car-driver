package io.github.bmd007.rpi.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class KeyframeToJpeg {

    private static final List<String> COMMAND = List.of(
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-f", "h264", "-i", "-",
        "-frames:v", "1", "-q:v", "4",
        "-f", "mjpeg", "-"
    );

    private KeyframeToJpeg() {
    }

    public static byte[] convert(byte[] keyframe) throws IOException {
        var process = new ProcessBuilder(COMMAND)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(keyframe);
        }
        try (var stdout = process.getInputStream()) {
            var jpeg = stdout.readAllBytes();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0 || jpeg.length == 0) {
                throw new IOException("ffmpeg failed to decode keyframe");
            }
            return jpeg;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while decoding keyframe", e);
        } finally {
            process.destroyForcibly();
        }
    }
}
