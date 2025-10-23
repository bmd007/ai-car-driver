package io.github.bmd007.ai.kale_kaj_driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
public class AiResource {

    private static final String SYSTEM_PROMPT = """
        You are controlling a robotic car with a front-facing camera.
        
        Your goal: [will be defined per request]
        
        You will receive images from the car's camera showing the first-person view.
        Based on what you see, decide the next move(s) to achieve the goal.
        
        RULES:
        - Respond ONLY with valid JSON in this exact format:
          {
            "thought": "your analysis of what you see and why you're taking these actions",
            "actions": ["FORWARD", "LEFT", "FORWARD"]
          }
        - Available actions: FORWARD, BACKWARD, LEFT, RIGHT
        - When you've achieved the goal, use an empty actions array: {"thought": "goal achieved", "actions": []}
        - Do NOT use empty actions array until the goal is fully achieved
        - If you can't see clearly or need more information, make your best guess based on what's visible
        - Be decisive - analyze the image and commit to a direction
        - Your response must be valid JSON only, no additional text
        
        Remember: Only and only and only respond with valid JSON, nothing else.
        The only acceptable response format is JSON, as exemplified below.
        Don't forget that your json response should start with { and end with }.
        
        Example valid responses:
        - {"thought": "I see an open path ahead", "actions": ["FORWARD"]}
        - {"thought": "Need to turn left to avoid obstacle", "actions": ["LEFT", "LEFT", "FORWARD"]}
        - {"thought": "Approaching the target on the right", "actions": ["FORWARD", "RIGHT", "FORWARD"]}
        - {"thought": "Goal achieved - reached destination", "actions": []}
        """;
    private final Sinks.Many<byte[]> imageSink = Sinks.many().multicast()
        .onBackpressureBuffer(1, false);


    private static final int MAX_ITERATIONS = 50;
    private final RpiService rpiService;
    private final ChatClient ollamaClient;
    private final ObjectMapper objectMapper;

    public AiResource(RpiService rpiService,
                      OllamaChatModel ollamaChatModel,
                      ObjectMapper objectMapper,
                      VertexAiGeminiChatModel vertexAiGeminiChatModel
    ) {
        this.rpiService = rpiService;
        this.ollamaClient = ChatClient.create(vertexAiGeminiChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT)
            .build();
        this.objectMapper = objectMapper;
    }

    public record ChatRequest(String goal) {
    }

    public record AgentStep(
        int iteration,
        String observation,
        String thought,
        List<String> actions,
        boolean completed) {

        public String printable() {
            return actions + " | " + thought;
        }
    }

    public record LlmResponse(String thought, List<String> actions) {
    }

    @GetMapping(path = "/llm-image-stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Flux<byte[]> llmImageStream() {
        log.info("New subscriber to LLM image stream");
        return imageSink.asFlux()
            .doOnCancel(() -> log.info("LLM image stream subscriber cancelled"));
    }

    @PostMapping(path = "/agent", produces = "text/event-stream")
    public Flux<String> agent(@RequestBody AiResource.ChatRequest request) {
        log.info("Starting agent with goal: {}", request.goal());

        var conversationHistory = new ArrayList<Message>();

        return Flux.range(0, MAX_ITERATIONS)
            .concatMap(iteration ->
                executeAgentStep(request.goal(), conversationHistory, iteration)
            )
            .takeUntil(AgentStep::completed)
            .map(AgentStep::printable)
            .doOnComplete(() -> log.info("Agent completed goal"))
            .doOnError(e -> log.error("Agent error", e));
    }

    private Mono<AgentStep> executeAgentStep(String goal, List<Message> history, int iteration) {
        return captureAndAnalyze(goal, history, iteration)
            .flatMap(step -> {
                if (!step.completed() && !step.actions().isEmpty()) {
                    return executeMovements(step.actions()).thenReturn(step);
                }
                return Mono.just(step);
            });
    }

    private Mono<AgentStep> captureAndAnalyze(String goal, List<Message> history, int iteration) {
        return rpiService.image()
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(imageBytes -> {
                // Emit raw image bytes to all subscribers
                imageSink.tryEmitNext(imageBytes);

                Media media = Media.builder()
                    .mimeType(MimeTypeUtils.IMAGE_JPEG)
                    .data(imageBytes)
                    .build();

                String userContent = iteration == 0
                    ? """
                    The goal is: %s
                    What do you see? What should be the next move?""".formatted(goal)
                    : "After the previous move, what do you see now? What's the next move?";

                var userMsg = new UserMessage(userContent)
                    .mutate()
                    .media(media)
                    .build();

                history.add(userMsg);

                Prompt prompt = Prompt.builder()
                    .messages(history)
                    .chatOptions(ChatOptions.builder()
                        .temperature(0.0)
                        .build())
                    .build();

                return ollamaClient.prompt(prompt)
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> String.join("", list))
                    .flatMap(response -> parseJsonResponse(response, iteration, history));
            });
    }

    private Mono<AgentStep> parseJsonResponse(String response, int iteration, List<Message> history) {
        return Mono.fromCallable(() -> {
            try {
                // Try to extract JSON if there's extra text
                String jsonStr = response.trim();
                int jsonStart = jsonStr.indexOf("{");
                int jsonEnd = jsonStr.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                }

                LlmResponse llmResponse = objectMapper.readValue(jsonStr, LlmResponse.class);

                log.info("Iteration {}: Thought: {}, Actions: {}",
                    iteration, llmResponse.thought(), llmResponse.actions());

                // Add assistant response to history
                history.add(new AssistantMessage(jsonStr));

                // Keep history manageable (last 6 messages = 3 exchanges)
                if (history.size() > 6) {
                    history.subList(0, history.size() - 6).clear();
                }

                boolean isCompleted = llmResponse.actions().isEmpty();

                return new AgentStep(
                    iteration,
                    "Image captured and analyzed",
                    llmResponse.thought(),
                    llmResponse.actions(),
                    isCompleted
                );
            } catch (Exception e) {
                log.error("Failed to parse JSON response: {}", response, e);
                // Fallback to old format if JSON parsing fails
                return new AgentStep(
                    iteration,
                    "Failed to parse JSON response",
                    "Parse error",
                    Collections.emptyList(),
                    true
                );
            }
        });
    }

    private Mono<Void> executeMovements(List<String> actions) {
        var validMoves = actions.stream()
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(RpiService.MOVE_DIRECTION::isMoveCommand)
            .map(RpiService.MOVE_DIRECTION::valueOf)
            .toList();

        if (validMoves.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(validMoves)
            .doOnNext(move -> log.info("Executing move: {}", move))
            .delayUntil(rpiService::moveTheRobot)
            .delayElements(Duration.ofMillis(1000))
            .then();
    }
}
