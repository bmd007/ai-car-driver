package io.github.bmd007.ai.kale_kaj_driver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
public class AiResource {

    private static final int MAX_ITERATIONS = 10;
    private static final long MOVE_DELAY_MS = 200l;
    private final RpiService rpiService;
    private final ChatClient ollamaClient;
    private static final String SYSTEM_PROMPT = """
        You are controlling a robotic car with a front-facing camera.
        
        Your goal: [will be defined per request]
        
        You will receive images from the car's camera showing the first-person view.
        Based on what you see, decide the next move(s) to achieve the goal.
        
        RULES:
        - Respond ONLY with directions: FORWARD, BACKWARD, LEFT, RIGHT
        - You can chain multiple moves with commas: "FORWARD, LEFT, FORWARD"
        - When you've achieved the goal, respond with: "STOP"
        - Do NOT respond with STOP until the goal is fully achieved
        - If you can't see clearly or need more information, make your best guess based on what's visible
        - Be decisive - analyze the image and commit to a direction
        
        Example valid responses:
        - "FORWARD"
        - "LEFT, LEFT, FORWARD"
        - "FORWARD, RIGHT, FORWARD"
        - "STOP"
        
        """;

    public AiResource(RpiService rpiService, OllamaChatModel ollamaChatModel) {
        this.rpiService = rpiService;
        this.ollamaClient = ChatClient.create(ollamaChatModel)
            .mutate()
            .defaultSystem(SYSTEM_PROMPT)
            .build();
    }

    public record ChatRequest(String goal) {
    }

    public record AgentStep(
        int iteration,
        String observation,
        String action,
        boolean completed) {

    }

    @PostMapping(path = "/agent", produces = "text/event-stream")
    public Flux<AgentStep> agent(@RequestBody AiResource.ChatRequest request) {
        log.info("Starting agent with goal: {}", request.goal());

        List<Message> conversationHistory = new ArrayList<>();

        return Flux.range(0, 2)
            .concatMap(iteration ->
                executeAgentStep(request.goal(), conversationHistory, iteration)
            )
            .takeUntil(AgentStep::completed)
            .doOnComplete(() -> log.info("Agent completed goal"))
            .doOnError(e -> log.error("Agent error", e));
    }

    private Mono<AgentStep> executeAgentStep(String goal, List<Message> history, int iteration) {
        return captureAndAnalyze(goal, history, iteration)
            .delayElement(Duration.ofMillis(MOVE_DELAY_MS))
            .flatMap(step -> {
                if (!step.completed() && !step.action().equals("STOP")) {
                    return executeMovements(step.action())
                        .thenReturn(step);
                }
                return Mono.just(step);
            });
    }

    private Mono<AgentStep> captureAndAnalyze(String goal, List<Message> history, int iteration) {
        return rpiService.image()
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(base64Image -> {
                Media media = Media.builder()
                    .mimeType(MediaType.IMAGE_JPEG)
                    .data(base64Image)
                    .build();

                String userContent = iteration == 0
                    ? """
                    The goal is: %s
                    What do you see? What should be the next move?""".formatted(goal)
                    : "After the previous move, what do you see now? What's the next move?";

                UserMessage userMsg = new UserMessage(userContent)
                    .mutate()
                    .media(media)
                    .build();

                history.add(userMsg);

                Prompt prompt = Prompt.builder()
                    .messages(history)
                    .chatOptions(ChatOptions.builder()
                        .temperature(0.1) // Low temperature for consistent decisions
                        .build())
                    .build();

                return ollamaClient.prompt(prompt)
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> String.join("", list))
                    .map(response -> {
                        String action = response.trim().toUpperCase();
                        log.info("Iteration {}: LLM response: {}", iteration, action);

                        // Add assistant response to history
                        history.add(new AssistantMessage(action));

                        // Keep history manageable (last 6 messages = 3 exchanges)
                        if (history.size() > 6) {
                            history.subList(0, history.size() - 6).clear();
                        }

                        boolean isCompleted = action.contains("STOP");

                        return new AgentStep(
                            iteration,
                            "Image captured and analyzed",
                            action,
                            isCompleted
                        );
                    });
            })
            .onErrorResume(e -> {
                log.error("Error in iteration {}: {}", iteration, e.getMessage());
                return Mono.just(new AgentStep(
                    iteration,
                    "Error: " + e.getMessage(),
                    "STOP",
                    true
                ));
            });
    }

    private Mono<Void> executeMovements(String actions) {
        List<String> moves = Arrays.stream(actions.split(","))
            .map(String::trim)
            .filter(move -> !move.equals("STOP"))
            .filter(RpiService.MOVE_DIRECTION::isMoveCommand)
            .toList();

        if (moves.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(moves)
            .concatMap(move ->
                Mono.fromRunnable(() -> {
                        log.info("Executing move: {}", move);
                        rpiService.moveTheRobot(RpiService.MOVE_DIRECTION.valueOf(move));
                    })
                    .delayElement(Duration.ofMillis(500)) // Delay between individual moves
                    .then()
            )
            .then();
    }
}
