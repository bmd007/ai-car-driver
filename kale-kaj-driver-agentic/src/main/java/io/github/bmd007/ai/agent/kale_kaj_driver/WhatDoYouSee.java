package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import org.springframework.beans.factory.annotation.Autowired;

@Agent(description = "Agent that drives a robotic car based on first-person camera images")
public class WhatDoYouSee {

    @Autowired
    RpiService rpiService;

    public record MoveCommand(String thoughts, RpiService.MOVE_DIRECTION direction) {
    }

    private static final String SYSTEM_PROMPT = """
        You are controlling a robotic car with a front-facing camera.
        
        Your goal: %s
        
        You will receive images from the car's camera showing the first-person view.
        Based on what you see, decide the next move(s) to achieve the goal.
        """;

    @Action
    public MoveCommand determineMoveCommand(UserInput userInput, OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .withToolObject(rpiService)
            .createObject(SYSTEM_PROMPT.formatted(userInput.getContent()), MoveCommand.class);
    }

    public record ThoughtsAndMove(RpiService.MOVE_DIRECTION direction, String thoughts) {
    }

    @Action
    @AchievesGoal(description = "Move the robotic car safely based on camera input and thoughts")
    public ThoughtsAndMove executeMoveCommand(MoveCommand moveCommand, OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .withToolObject(rpiService)
            .createObject("""
                Move the robotic car based on the following command: %s, only if the thoughts indicate it's safe to do so.
                Respond with your thought about the move.
                """.formatted(moveCommand), ThoughtsAndMove.class);
    }
}
