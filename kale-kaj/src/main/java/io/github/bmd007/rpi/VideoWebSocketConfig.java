package io.github.bmd007.rpi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.Map;

@Configuration
public class VideoWebSocketConfig {

    @Bean
    public HandlerMapping videoWebSocketMapping() {
        WebSocketHandler handler = session -> session.send(
            ActuatorResource.h264Stream()
                .map(data -> session.binaryMessage(factory -> factory.wrap(data)))
        );
        return new SimpleUrlHandlerMapping(Map.of("/v3/h264", handler), -1);
    }
}
