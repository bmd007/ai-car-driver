package io.github.bmd007.rpi.kale_kaj.service;

import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Simple Java API wrapper for rpicam-vid command.
 * Provides basic functionality for capturing video on Raspberry Pi.
 */
public class RpiCamVid {

    private String outputDir = "/tmp";
    private int width = 640;
    private int height = 480;
    private int timeout = 10000; // 10 seconds default
    private String encoding = "h264";
    private int framerate = 30;
    private boolean verbose = false;

    public RpiCamVid setOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public RpiCamVid setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public RpiCamVid setTimeout(int timeoutMs) {
        this.timeout = timeoutMs;
        return this;
    }

    public RpiCamVid setEncoding(String encoding) {
        this.encoding = encoding.toLowerCase();
        return this;
    }

    public RpiCamVid setFramerate(int framerate) {
        this.framerate = Math.max(1, framerate);
        return this;
    }

    public RpiCamVid setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public Mono<File> captureVideoAsync(String filename) {
        return Mono.fromCallable(() -> captureVideo(filename));
    }

    public Mono<InputStream> streamVideoAsync() {
        return Mono.fromCallable(this::streamVideo);
    }

    public File captureVideo(String filename) throws IOException {
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        String fullPath = Paths.get(outputDir, filename).toString();
        List<String> command = buildCommand(fullPath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            if (verbose) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("rpicam-vid: " + line);
                    }
                }
            }

            boolean finished = process.waitFor(timeout + 5000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Video capture timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Video capture failed with exit code: " + exitCode);
            }

            File outputFile = new File(fullPath);
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("Video file was not created or is empty: " + fullPath);
            }

            return outputFile;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Video capture was interrupted", e);
        }
    }

    public InputStream streamVideo() throws IOException {
        List<String> command = buildCommand("-"); // Output to stdout

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            return new ProcessInputStream(process);
        } catch (Exception e) {
            throw new IOException("Failed to start video stream", e);
        }
    }

    private List<String> buildCommand(String outputPath) {
        List<String> command = new ArrayList<>();
        command.add("rpicam-vid");

        command.add("--output");
        command.add(outputPath);

        command.add("--width");
        command.add(String.valueOf(width));
        command.add("--height");
        command.add(String.valueOf(height));

        command.add("--timeout");
        command.add(String.valueOf(timeout));

        command.add("--framerate");
        command.add(String.valueOf(framerate));

        command.add("--codec");
        command.add(encoding);

        if (verbose) {
            command.add("--verbose");
        }

        command.add("--nopreview");

        return command;
    }

    public static boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("rpicam-vid", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static class ProcessInputStream extends InputStream {
        private final Process process;
        private final InputStream inputStream;

        public ProcessInputStream(Process process) {
            this.process = process;
            this.inputStream = process.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inputStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                inputStream.close();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
    }
}
