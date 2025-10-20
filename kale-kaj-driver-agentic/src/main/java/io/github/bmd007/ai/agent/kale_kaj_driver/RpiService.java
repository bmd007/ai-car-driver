package io.github.bmd007.ai.agent.kale_kaj_driver;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Slf4j
@Service
public class RpiService {

    private final WebClient client;

    public RpiService(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder
            .baseUrl("http://192.168.1.165:8080")
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                    .maxInMemorySize(1024 * 1024 * 100) // 10 MB
                )
                .build()
            )
            .build();
    }

    @Tool(description = "Thought the robot in the specified direction")
    public void moveTheRobot(MOVE_DIRECTION direction) {
        client.post()
            .uri("/move?command=" + direction.name())
            .retrieve()
            .bodyToMono(Void.class)
            .subscribe();
    }

    @Tool(description = "Get a picture from the robot front first person camera, a byte arrays representing JPEG image")
    public byte[] image() {
        return client.get()
            .uri("/v3/capture-image")
            .retrieve()
            .bodyToMono(byte[].class)
            .retry(2)
            .onErrorReturn(new byte[]{})
            .block();
    }

    public enum MOVE_DIRECTION {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT;

        public static boolean isMoveCommand(String command) {
            return command != null && (command.equals(FORWARD.name()) ||
                command.equals(BACKWARD.name()) ||
                command.equals(LEFT.name()) ||
                command.equals(RIGHT.name()));
        }
    }
}

