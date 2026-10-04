package rag.example.rag_implementation.services;

import org.springframework.stereotype.Service;
import rag.example.rag_implementation.dto.ConversationMessageDTO;
import rag.example.rag_implementation.llm.LLMMessage;
import rag.example.rag_implementation.repository.ConversationMessageRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversation history, persisted in MySQL and keyed by chatbotId - a
 * conversation belongs to a (user, chatbot) pair, not an ephemeral
 * per-browser-tab sessionId. Reopening the same chatbot, even after
 * logging out and back in or from a different device, continues the
 * same thread. ConversationMessageRepository handles the 6-hour
 * retention window and the scheduled physical cleanup.
 */
@Service
public class ConversationMemoryService {

    private final ConversationMessageRepository conversationMessageRepository;

    public ConversationMemoryService(ConversationMessageRepository conversationMessageRepository) {
        this.conversationMessageRepository = conversationMessageRepository;
    }

    public List<LLMMessage> getHistory(Long chatbotId) {

        return conversationMessageRepository.getRecentMessages(chatbotId).stream()
                .map(m -> new LLMMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    public List<ConversationMessageDTO> getDisplayHistory(Long chatbotId) {

        return conversationMessageRepository.getRecentMessages(chatbotId).stream()
                .map(m -> ConversationMessageDTO.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .model(m.getModel())
                        .totalTokens(m.getTotalTokens())
                        .latencyMs(m.getLatencyMs())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public void appendTurn(
            Long chatbotId,
            Long userId,
            String userMessage,
            String assistantMessage,
            String model,
            Integer totalTokens,
            Long latencyMs) {

        conversationMessageRepository.saveMessage(
                chatbotId, userId, "user", userMessage, null, null, null);

        conversationMessageRepository.saveMessage(
                chatbotId, userId, "assistant", assistantMessage, model, totalTokens, latencyMs);
    }
}
