package io.github.bmd007.ai.kale_kaj_driver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
//@RestController
public class AiResourceServlet {

    private final ChatClient geminiClient;
    private static final String SYSTEM_PROMPT2 = """
        You are in charge of moving a robotic car around.
        You have access to a tool to move the car a little in the specified direction: FORWARD, BACKWARD, LEFT, RIGHT.
        You will be asked to use achieve some goal, like reaching a specific object.
        You have access to another tool to capture images from the car's camera.
        Which feels like a first person view from the car.
        Use the image capture tool to see what is in front of the car and decide how to move the car.
        Use what you see in the image to decide how to move the car.
        Always describe what you see in the image.
        say clearly when you are done and have reached your goal.
        say clearly when you can't use the tools too see the image.
        Remember you can call the tools as many times as you need to achieve your goal.
        Keep your response stream going until you have reached your goal.
        """;
    private final TooBox toolBox;

    public AiResourceServlet(VertexAiGeminiChatModel vertexAiGeminiChatModel,
                             TooBox toolBox) {
        this.toolBox = toolBox;
        this.geminiClient = ChatClient.create(vertexAiGeminiChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT2)
            .defaultTools(toolBox)
            .build();
    }

    public record ChatRequest(String input) {
    }
    public record ChatResponse(String response) {
    }

//    @PostMapping(path = "/chat", produces = "text/event-stream")
    @PostMapping(path = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        var msg = new UserMessage(request.input())
            .mutate()
            .build();
        var prompt = Prompt.builder()
            .messages(msg)
            .chatOptions(ChatOptions.builder()
                .temperature(0d)
                .build())
            .build();
        var response = geminiClient.prompt(prompt)
            .tools(toolBox)
            .call()
            .content();
        return new ChatResponse(response);
    }
}
