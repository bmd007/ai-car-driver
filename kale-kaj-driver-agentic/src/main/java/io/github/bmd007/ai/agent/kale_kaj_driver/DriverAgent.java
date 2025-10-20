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
@Agent(description = "Agent that drives a robotic car based on first-person camera images")
public class DriverAgent {

    @Autowired
    RpiService rpiService;

    @With
    public record ObservationResult(String observation, RpiService.MOVE_DIRECTION moveDirection) {
    }

    @Action
    public ObservationResult determineMoveCommand(UserInput userInput, OperationContext context) {
        var res = context.ai()
            .withAutoLlm()
            .withToolObject(rpiService)
            .createObject("""
                You are a vigilant robot/car driver. You drive the car based on what you see from the car's camera.
                
                Your goal: %s
                
                Get an image from the car's camera.
                Explain what you see looking at each image.
                Based on what you see, decide the best move command to achieve the goal.
                """.formatted(userInput.getContent()), ObservationResult.class);
        log.info("Determined move command: {}", res);
        return res;
    }

    public record CarMoved(String thought) {
    }

    @Action
    @AchievesGoal(description = "Move the car and say what you think")
    public CarMoved executeMoveCommand(ObservationResult observationResult,
                                       UserInput userInput,
                                       OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .withToolObject(rpiService)
            .createObject("""
                Move the car based on the command: %s.
                Explain how you compare the observation %s with the goal %s .
                """.formatted(observationResult.moveDirection,
                observationResult.observation(),
                userInput.getContent()
            ), CarMoved.class);
    }
}
