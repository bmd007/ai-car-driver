///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS artifact-registry-central=artifactregistry://europe-north1-maven.pkg.dev/nordnet-artifacts/maven-central-europe-north1
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter-web:3.5.7
//FILES application.yaml

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@RestController
public class SpringJbangHelloNordnet {

    @Value("${app.name:Default App}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.greeting.message:Hello}")
    private String greetingMessage;

    public static void main(String[] args) {
        SpringApplication.run(HelloWorld.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return greetingMessage + " World from " + appName + " v" + appVersion + "!";
    }

    @GetMapping("/greet")
    public Greeting greet() {
        return new Greeting(greetingMessage, "Welcome to " + appName + "!", appVersion);
    }

    @GetMapping("/config")
    public ConfigInfo config() {
        return new ConfigInfo(appName, appVersion, greetingMessage);
    }

    record Greeting(String message, String description, String version) {}
    record ConfigInfo(String appName, String appVersion, String greetingMessage) {}
}
