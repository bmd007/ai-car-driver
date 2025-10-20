package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Agent(description = "Agent that explains its observations from the car's camera")
public class WhatYouSee {

    @Autowired
    RpiService rpiService;

    public record ObservationResult(String observation) {
    }

    @AchievesGoal(description = "Explain what you see")
    @Action
    public ObservationResult captureAndExplain(UserInput userInput, OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .withToolObject(rpiService)
            .createObject("""
                Get an image and explain what you see looking at the image, while answering the question: "%s".
                """.formatted(userInput.getContent()), ObservationResult.class);
    }
}
