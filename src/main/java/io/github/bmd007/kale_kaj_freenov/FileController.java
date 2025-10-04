package io.github.bmd007.kale_kaj_freenov;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class FileController {

    private static final String DIRECTORY = "/home/pi/freenov-kale-kaj/tmp/";

    @GetMapping(value = "/files", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<List<String>> listFiles() {
        File dir = new File(DIRECTORY);
        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.status(404).body(List.of());
        }
        List<String> files = Arrays.stream(dir.listFiles())
            .filter(File::isFile)
            .map(File::getName)
            .map(a -> "http://192.168.1.165:8080/files/" + a)
            .map(a -> "<a href=\"" + a + "\">" + a + "/a> <br> </br>")
            .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        File file = new File(DIRECTORY, filename);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.status(404).build();
        }
        FileSystemResource resource = new FileSystemResource(file);
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
}
