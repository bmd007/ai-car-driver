package io.github.bmd007.kale_kaj_freenov;

import com.pi4j.Pi4J;
import io.github.bmd007.kale_kaj_freenov.service.PCA9685;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
@SpringBootApplication
public class FreenovKaleKaj {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FreenovKaleKaj.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }

    private static final int I2C_BUS = 1;
    private static final int PCA9685_ADDR = 0x40;

    @Bean
    public PCA9685 pca9685() throws InterruptedException {
        return new PCA9685(Pi4J.newAutoContext(), I2C_BUS, PCA9685_ADDR);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(authorize -> authorize
                .anyExchange().permitAll()
            )
            .oauth2Login(withDefaults());

        return http.build();
    }
}
