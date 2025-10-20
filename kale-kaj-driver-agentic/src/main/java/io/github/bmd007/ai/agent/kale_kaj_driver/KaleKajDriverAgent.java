package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.annotation.LoggingThemes;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@EnableAgents(
    loggingTheme = LoggingThemes.STAR_WARS
)
@SpringBootApplication
public class KaleKajDriverAgent {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(KaleKajDriverAgent.class);
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
                    )
                )
                .build(WhatDoYouSee.Move.class);
        var input = new UserInput("Keep moving and fetch pictures until you see a human.");
        var story = invocation.invoke(input);
    }
}
