package io.github.bmd007.ai.kale_kaj_driver;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Slf4j
//@Service
public class TooBox {

    private final RestClient client;

    public TooBox(RestClient.Builder webClientBuilder) {
        this.client = webClientBuilder
            .baseUrl("https://kalekaj-bmd.eu1.pitunnel.net")
            .build();
    }

    @Tool(description = "Move the robot a little in the specified direction: FORWARD, BACKWARD, LEFT, RIGHT")
    public void moveTheRobot(MOVE_DIRECTION direction) {
        client.post()
            .uri("/move?command=" + direction.name())
            .retrieve();
    }

//    @Tool(description = "Get the video feed from the robot front first person camera, as a stream of base64 string representing byte arrays representing video frames")
//    public Flux<String> videoFeed() {
//        return webClient.get()
//            .uri("/v3/video-stream")
//            .retrieve()
//            .bodyToFlux(byte[].class)
//            .map(Base64.getEncoder()::encodeToString)
//            .take(10);
//    }

    @Tool(description = "Get a picture from the robot front first person camera, as a base64 string representing byte arrays representing JPEG image")
    public String image() {
        var bytes = client.get()
            .uri("/v3/capture-image")
            .retrieve()
            .body(byte[].class);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public enum MOVE_DIRECTION {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
    }
}

