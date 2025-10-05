package io.github.bmd007.kale_kaj_freenov.resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static io.github.bmd007.kale_kaj_freenov.Application.PICS_DIRECTORY;

@RestController
public class FileResource {

    @GetMapping(value = "/files")
    public ResponseEntity<List<String>> listFiles() {
        File dir = new File(PICS_DIRECTORY);
        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.status(404).body(List.of());
        }
        List<String> files = Arrays.stream(dir.listFiles())
            .filter(File::isFile)
            .map(File::getName)
            .toList();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<byte[]> getFile(@PathVariable String filename) throws IOException {
        File file = new File(PICS_DIRECTORY, filename);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.status(404).build();
        }
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
            .body(Files.readAllBytes(file.toPath()));
    }
}
