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
 * Simple Java API wrapper for rpicam-still command.
 * Provides basic functionality for capturing still images on Raspberry Pi.
 */
public class RpiCamStill {

    private String outputDir = "/tmp";
    private int width = 640;
    private int height = 480;
    private int timeout = 5000; // 5 seconds default
    private String encoding = "jpg";
    private int quality = 90;
    private boolean verbose = false;

    /**
     * Set output directory for captured images.
     * @param outputDir Directory path to save images
     * @return this instance for method chaining
     */
    public RpiCamStill setOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    /**
     * Set image dimensions.
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return this instance for method chaining
     */
    public RpiCamStill setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Set capture timeout in milliseconds.
     * @param timeoutMs Timeout in milliseconds
     * @return this instance for method chaining
     */
    public RpiCamStill setTimeout(int timeoutMs) {
        this.timeout = timeoutMs;
        return this;
    }

    /**
     * Set image encoding format.
     * @param encoding Image format (jpg, png, bmp, etc.)
     * @return this instance for method chaining
     */
    public RpiCamStill setEncoding(String encoding) {
        this.encoding = encoding.toLowerCase();
        return this;
    }

    /**
     * Set JPEG quality (0-100).
     * @param quality Quality level for JPEG compression
     * @return this instance for method chaining
     */
    public RpiCamStill setQuality(int quality) {
        this.quality = Math.max(0, Math.min(100, quality));
        return this;
    }

    /**
     * Enable or disable verbose output.
     * @param verbose Enable verbose logging
     * @return this instance for method chaining
     */
    public RpiCamStill setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public Mono<File> captureStillAsync(String filename) {
        return Mono.fromCallable(() -> captureStill(filename));
    }

    public Mono<InputStream> captureToStreamAsync() {
        return Mono.fromCallable(this::captureToStream);
    }

    /**
     * Capture a still image and save to file.
     * @param filename Name of the output file (without path)
     * @return File object representing the captured image
     * @throws IOException if capture fails or file cannot be created
     */
    public File captureStill(String filename) throws IOException {
        // Ensure output directory exists
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // Build full output path
        String fullPath = Paths.get(outputDir, filename).toString();

        // Build command
        List<String> command = buildCommand(fullPath);

        // Execute command
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            // Capture output if verbose
            if (verbose) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("rpicam-still: " + line);
                    }
                }
            }

            // Wait for completion
            boolean finished = process.waitFor(timeout + 5000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Camera capture timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Camera capture failed with exit code: " + exitCode);
            }

            // Verify file was created
            File outputFile = new File(fullPath);
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("Image file was not created or is empty: " + fullPath);
            }

            return outputFile;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Camera capture was interrupted", e);
        }
    }

    /**
     * Capture image directly to InputStream (useful for streaming).
     * @return InputStream containing the image data
     * @throws IOException if capture fails
     */
    public InputStream captureToStream() throws IOException {
        List<String> command = buildCommand("-"); // Output to stdout

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            return new ProcessInputStream(process);
        } catch (Exception e) {
            throw new IOException("Failed to start camera capture", e);
        }
    }

    /**
     * Build the rpicam-still command with current settings.
     * @param outputPath Output file path or "-" for stdout
     * @return List of command arguments
     */
    private List<String> buildCommand(String outputPath) {
        List<String> command = new ArrayList<>();
        command.add("rpicam-still");

        // Output
        command.add("--output");
        command.add(outputPath);

        // Dimensions
        command.add("--width");
        command.add(String.valueOf(width));
        command.add("--height");
        command.add(String.valueOf(height));

        // Timeout
        command.add("--timeout");
        command.add(String.valueOf(timeout));

        // Encoding
        command.add("--encoding");
        command.add(encoding);

        // Quality (for JPEG)
        if ("jpg".equals(encoding) || "jpeg".equals(encoding)) {
            command.add("--quality");
            command.add(String.valueOf(quality));
        }

        // Verbose
        if (verbose) {
            command.add("--verbose");
        }

        // No preview by default
        command.add("--nopreview");

        return command;
    }

    /**
     * Test if rpicam-still is available on the system.
     * @return true if rpicam-still command is available
     */
    public static boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("rpicam-still", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * InputStream wrapper that properly handles process cleanup.
     */
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
