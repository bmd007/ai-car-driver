package io.github.bmd007.ai.kale_kaj_driver;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TooBox {

    private final WebClient webClient;

    public TooBox(WebClient.Builder webClientBuilder) {
        webClient = webClientBuilder
            .baseUrl("https://kalekaj-bmd.eu1.pitunnel.net")
            .build();
    }

    @Tool(description = "Move the robot a little in the specified direction: FORWARD, BACKWARD, LEFT, RIGHT")
    public void moveTheRobot(MOVE_DIRECTION direction) {
        webClient.post()
            .uri("/move?command=" + direction.name())
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }

    @Tool(description = "Get the video feed from the robot as a stream of byte arrays representing JPEG images")
    public Flux<byte[]> videoFeed() {
        return webClient.post()
            .uri("/v3/video-stream")
            .retrieve()
            .bodyToFlux(byte[].class);
    }

    @Tool(description = "Get a single image from the robot as a byte array representing a JPEG image")
    public Mono<byte[]> takeImage() {
        return webClient.post()
            .uri("/image")
            .retrieve()
            .bodyToMono(byte[].class);
    }

    public enum MOVE_DIRECTION {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
    }
}

