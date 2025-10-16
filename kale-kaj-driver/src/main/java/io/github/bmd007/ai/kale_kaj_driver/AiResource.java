package io.github.bmd007.ai.kale_kaj_driver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
public class AiResource {

    private final TooBox toolBox;
    private final ChatClient ollamaClient;
    private final ChatClient openAiClient;
    private static final String SYSTEM_PROMPT = """
        You are in charge of moving a robotic car around. You will look at the video feed and decide how to move the car.
        You have access to a tool to move the car a little in the specified direction: FORWARD, BACKWARD, LEFT, RIGHT.
        You will only use the tool if you decide the car needs to move.
        You will not move the car if it is already moving.
        You will not move the car if there is an obstacle in front of it.
        You will not move the car if it is too close to an obstacle.
        You will be asked to use achieve some goal, liking reaching a specific object in the video feed.
        You will be provided with a video feed from the car.
        Use what you see in the video feed to decide how to move the car.
        Always think step by step and decide what to do.
        Always describe what you see in the video feed.
        Always describe your reasoning before you decide to use the tool.
        say clearly when you are done and have reached your goal.
        say cleanly when you are going to use the tool and what parameters you are using.
        say clearly whe you can't use the tools too see the video feed.
        The video feed only last 3 secs, you need to ask for a new video feed every time you want to see it.
        """;

    public AiResource(TooBox toolBox,
                      OllamaChatModel ollamaChatModel,
                      OpenAiChatModel openAiChatModel) {
        this.toolBox = toolBox;
        openAiClient = ChatClient.create(openAiChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT)
            .build();
        this.ollamaClient = ChatClient.create(ollamaChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT)
            .build();
    }

    public record ChatRequest(String input) {
    }

    @PostMapping(path = "/chat", produces = "text/event-stream")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        var prompt = Prompt.builder()
            .content(request.input())
            .chatOptions(ChatOptions.builder()
                .temperature(0d)
                .build())
            .build();
        return ollamaClient.prompt(prompt)
            .tools(toolBox)
            .stream()
            .content();
    }
}
