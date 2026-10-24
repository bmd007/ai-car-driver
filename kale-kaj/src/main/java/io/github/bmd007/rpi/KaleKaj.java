package io.github.bmd007.rpi;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import io.github.bmd007.rpi.service.PCA9685;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication
public class KaleKaj {
    private static final int I2C_BUS = 1;
    private static final int PCA9685_ADDR = 0x40;

    static void main(String[] args) {
        var app = new SpringApplication(KaleKaj.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    @Bean(destroyMethod = "shutdown")
    public Context pi4j() {
        return Pi4J.newAutoContext();
    }

    @Bean
    public PCA9685 pca9685(Context pi4j) throws InterruptedException {
        return new PCA9685(pi4j, I2C_BUS, PCA9685_ADDR);
    }
}
