package com.aipclm.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * <p>Clients connect to {@code /ws} (with SockJS fallback) and subscribe to
 * destinations prefixed with {@code /topic/}. The backend publishes real-time
 * simulation data to per-session topics so every connected dashboard receives
 * sub-second updates without HTTP polling.</p>
 *
 * <h3>Topic layout</h3>
 * <ul>
 *   <li>{@code /topic/session/{id}/state}            — latest telemetry + cognitive + risk + recommendations</li>
 *   <li>{@code /topic/session/{id}/cognitive-history} — new cognitive history entry (append on client)</li>
 *   <li>{@code /topic/session/{id}/risk-history}      — new risk history entry (append on client)</li>
 *   <li>{@code /topic/sessions}                       — session list updates (create / status change)</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for /topic destinations
        registry.enableSimpleBroker("/topic");
        // Prefix for messages FROM the client (not used yet, but available for future)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5173",
                        "http://127.0.0.1:5174"
                )
                .withSockJS();
    }
}
