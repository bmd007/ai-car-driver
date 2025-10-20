package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.models.OpenAiCompatibleModelFactory;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@Slf4j
@EnableAgents(
//    loggingTheme = LoggingThemes.STAR_WARS
)
@SpringBootApplication
public class KaleKajDriverAgenticApp {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(KaleKajDriverAgenticApp.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }

    @Bean
    public OpenAiCompatibleModelFactory customOpenAiCompatibleModels(ObservationRegistry observationRegistry,
                                                                     @Value("${GEMINI_OPENAI_COMPATIBLE_BASE_URL}") String baseUrl,
                                                                     @Value("${GEMINI_OPENAI_COMPATIBLE_API_KEY}") String apiKey) {
        return new OpenAiCompatibleModelFactory(baseUrl, apiKey,
            "v1beta/openai/chat/completions", null, observationRegistry);
    }


    @Autowired
    AgentPlatform agentPlatform;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        var invocation =
            AgentInvocation.builder(agentPlatform)
                .options(options -> options
                    .verbosity(verbosity -> verbosity
                        .showPrompts(true)
                        .showLlmResponses(true)
                        .debug(true)
                        .build()
                    )
                    .build()
                )
                .build(DriverAgent.CarMoved.class);
        var input = new UserInput("keep moving the car until you end up in kitchen close to the oven?");
        invocation.invoke(input);
    }
}
