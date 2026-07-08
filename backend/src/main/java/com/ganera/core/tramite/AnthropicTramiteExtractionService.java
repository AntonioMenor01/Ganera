package com.ganera.core.tramite;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AnthropicTramiteExtractionService implements TramiteExtractionService {

    private final ChatClient chatClient;

    public AnthropicTramiteExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public TramiteExtraido extraer(String mensajeOriginal) {
        throw new UnsupportedOperationException(
                "Extraccion de tramites via IA pendiente de implementar (Prompt 3b)");
    }
}
