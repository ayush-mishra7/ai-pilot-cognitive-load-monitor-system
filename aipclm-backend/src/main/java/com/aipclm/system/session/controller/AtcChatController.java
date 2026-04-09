package com.aipclm.system.session.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STOMP controller for real-time ATC ↔ Flight text messaging.
 *
 * <p>Messages sent to {@code /app/chat/{sessionId}} are rebroadcast to all
 * subscribers of {@code /topic/session/{sessionId}/chat}.</p>
 *
 * <p>No persistence — this is live cockpit-to-tower communication only.</p>
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class AtcChatController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Receive a chat message from a client and rebroadcast it.
     * Accepts a raw Map to avoid any Jackson DTO deserialization issues.
     */
    @MessageMapping("/chat/{sessionId}")
    public void handleChatMessage(@DestinationVariable String sessionId, Map<String, Object> incoming) {
        String sender = incoming.getOrDefault("sender", "UNKNOWN").toString();
        String text   = incoming.getOrDefault("text", "").toString();

        Map<String, String> outgoing = new LinkedHashMap<>();
        outgoing.put("sender", sender);
        outgoing.put("text", text);
        outgoing.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/chat", outgoing);
        log.info("[ATC-Chat] {} → session {}: {}", sender, sessionId, text);
    }
}
