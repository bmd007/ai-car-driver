package io.github.bmd007.ai.kale_kaj_driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KaleKajAiDriver {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(KaleKajAiDriver.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }
}
