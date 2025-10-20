package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
                .build(WriteAndReviewAgent.ReviewedStory.class);
        var input = new UserInput("do you see?");
        invocation.invoke(input);
    }
}
