package io.github.bmd007.ai.kale_kaj_driver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
public class AiResource {

    private final RpiService rpiService;
    private final ChatClient ollamaClient;
    private static final String SYSTEM_PROMPT2 = """
        You are in charge of moving a robotic car around.
        You will be asked to use achieve some goal, like reaching a specific object.
        You have be given an image from the car's camera.
        Which feels like a first person view from the car.
        Use the image to see what is in front of the car and decide how to move the car.
        Say clearly when you can't use the tools too see the image.
        Otherwise, your response must only be a DIRECTION, a specified direction: FORWARD, BACKWARD, LEFT, RIGHT.
        DO NOT RESPOND WITH ANYTHING ELSE.
        If you want to car to move more than one step, repeat the directions as many times as needed, separating them by commas.
        LEFT, RIGHT, FORWARD, BACKWARD are the only valid directions. They must be in one word.
        Example valid responses: "FORWARD", "LEFT, LEFT, FORWARD", "FORWARD, RIGHT, FORWARD, LEFT"
        """;

    public AiResource(RpiService rpiService, OllamaChatModel ollamaChatModel) {
        this.rpiService = rpiService;
        this.ollamaClient = ChatClient.create(ollamaChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT2)
            .build();
    }

    public record ChatRequest(String input) {
    }

    private final Set<String> messages = ConcurrentHashMap.newKeySet();

    @PostMapping(path = "/agent", produces = "text/event-stream")
    public Flux<String> agent(@RequestBody ChatRequest request) {
        return rpiService.image()
            .subscribeOn(Schedulers.boundedElastic())
            .map(s -> Media.builder()
                .mimeType(MediaType.IMAGE_JPEG)
                .data(s)
                .build())
            .map(media -> new UserMessage(request.input())
                .mutate()
                .media(media)
                .build())
            .map(msg -> Prompt.builder()
                .messages(msg)
                .chatOptions(ChatOptions.builder()
                    .temperature(0d)
                    .build())
                .build())
            .flatMapMany(prompt ->
                ollamaClient.prompt(prompt)
                    .stream()
                    .content()
                    .collectList()
            )
            .map(list -> String.join("", list))
            .map(String::trim)
            .map(String::toUpperCase)
            .flatMapIterable(moves -> Arrays.asList(moves.split(",")))
            .map(String::trim)
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(move -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                rpiService.moveTheRobot(RpiService.MOVE_DIRECTION.valueOf(move));
            });
    }
}
