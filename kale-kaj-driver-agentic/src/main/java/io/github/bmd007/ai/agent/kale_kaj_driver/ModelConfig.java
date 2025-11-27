package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.spi.config.spring.AgentPlatformConfiguration;
import com.embabel.common.ai.model.Llm;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PerTokenPricingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;


@Configuration
@AutoConfigureBefore(AgentPlatformConfiguration.class)
public class ModelConfig {

    private static final String PROVIDER = "Google";
    private static final OptionsConverter<ToolCallingChatOptions> OPTIONS_CONVERTER = new VertexAiOptionsConverter();

    private final VertexAiGeminiChatModel chatModel;

    public ModelConfig(VertexAiGeminiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Bean
    public Llm gemini30Pro() {
        final var knowledgeCutoffDate = LocalDate.of(2025, 1, 1);
        final var pricePerMillionInputTokens = 1.25;
        final var pricePerMillionOutputTokens = 10.00;

        return new Llm(
            "gemini-3.0-pro",
            PROVIDER,
            chatModel,
            OPTIONS_CONVERTER,
            knowledgeCutoffDate,
            List.of(),
            new PerTokenPricingModel(pricePerMillionInputTokens, pricePerMillionOutputTokens)
        );
    }
//
//    @Bean
//    public Llm gemini25Pro() {
//        final var knowledgeCutoffDate = LocalDate.of(2025, 1, 1);
//        final var pricePerMillionInputTokens = 1.25;
//        final var pricePerMillionOutputTokens = 10.00;
//
//        return new Llm(
//            "gemini-2.5-pro",
//            PROVIDER,
//            chatModel,
//            OPTIONS_CONVERTER,
//            knowledgeCutoffDate,
//            List.of(),
//            new PerTokenPricingModel(pricePerMillionInputTokens, pricePerMillionOutputTokens)
//        );
//    }
//
//    @Bean
//    public Llm gemini25Flash() {
//        final var knowledgeCutoffDate = LocalDate.of(2025, 1, 1);
//        final var pricePerMillionInputTokens = 0.30;
//        final var pricePerMillionOutputTokens = 2.50;
//
//        return new Llm(
//            "gemini-2.5-flash",
//            PROVIDER,
//            chatModel,
//            OPTIONS_CONVERTER,
//            knowledgeCutoffDate,
//            List.of(),
//            new PerTokenPricingModel(pricePerMillionInputTokens, pricePerMillionOutputTokens)
//        );
//    }
//
//    @Bean
//    public Llm gemini25FlashLite() {
//        final var knowledgeCutoffDate = LocalDate.of(2025, 1, 1);
//        final var pricePerMillionInputTokens = 0.10;
//        final var pricePerMillionOutputTokens = 0.40;
//
//        return new Llm(
//            "gemini-2.5-flash-lite",
//            PROVIDER,
//            chatModel,
//            OPTIONS_CONVERTER,
//            knowledgeCutoffDate,
//            List.of(),
//            new PerTokenPricingModel(pricePerMillionInputTokens, pricePerMillionOutputTokens)
//        );
//    }

    // Converts Embabel's LlmOptions to Spring AI's ToolCallingChatOptions for Vertex AI.
    private static final class VertexAiOptionsConverter implements OptionsConverter<ToolCallingChatOptions> {
        @Override
        public ToolCallingChatOptions convertOptions(LlmOptions options) {
            return ToolCallingChatOptions.builder()
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .topK(options.getTopK())
                .maxTokens(options.getMaxTokens())
                .build();
        }
    }
}

