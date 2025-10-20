package io.github.bmd007.ai.agent.kale_kaj_driver;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;

import java.util.Set;

@Agent(description = "Agent that writes and reviews stories")
public class WriteAndReviewAgent {

    public record Story(String text, String author, String name, String Id) {
    }

    Persona persona = new Persona(
        "Alex the Analyst",
        "A detail-oriented data analyst with expertise in financial markets",
        "Professional yet approachable, uses clear explanations",
        "Help users understand complex financial data through clear analysis"
    );

    RoleGoalBackstory agent = RoleGoalBackstory.withRole("Senior Software Engineer")
        .andGoal("Write clean, maintainable code")
        .andBackstory("10+ years experience in enterprise software development");

    @Action
    public WriteAndReviewAgent.Story writeStory(UserInput userInput, OperationContext context) {
        return context.ai()
            .withAutoLlm()
            .withToolGroups(Set.of(CoreToolGroups.WEB))
            .withPromptElements(persona)
            .createObject("""
                You are a creative writer who aims to delight and surprise.
                Write a story about %s
                """.formatted(userInput.getContent()), WriteAndReviewAgent.Story.class);
    }

    public record ReviewedStory(WriteAndReviewAgent.Story story, double score) {
    }

    @AchievesGoal(description = "Review a story")
    @Action
    public ReviewedStory reviewStory(WriteAndReviewAgent.Story story, OperationContext context) {
        return context.ai()
            .withLlmByRole("reviewer")
            .withToolGroups(Set.of(CoreToolGroups.MATH))
            .withPromptElements(agent)
            .createObject("""
                        You are a meticulous editor.
                        Carefully review this story:
                        %s
                    """.formatted(story.text),
                ReviewedStory.class);
    }
}
