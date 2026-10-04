package rag.example.rag_implementation.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rag.example.rag_implementation.services.PineconeIntegrationService;
import rag.example.rag_implementation.llm.LLMClient;
import rag.example.rag_implementation.llm.LLMMessage;
import rag.example.rag_implementation.llm.LLMRequestDO;
import rag.example.rag_implementation.llm.LLMResponse;
import rag.example.rag_implementation.services.EmbeddingService;
// import rag.example.rag_implementation.services.LLMService;
import rag.example.rag_implementation.dto.ChatResponseDTO;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagService {
    @Autowired
    private PineconeIntegrationService pineconeService;

    @Autowired
    private EmbeddingService embeddingService;

   @Autowired
    private LLMClient llmClient;

    @Autowired
    private ConversationMemoryService conversationMemoryService;

    @Autowired
    private UserService userService;

    public ChatResponseDTO askQuestion(Long chatbotId, String question) {

        // Conversation identity is (user, chatbot) - whoever is asking is
        // already guaranteed to be this chatbot's owner by the ownership
        // check the controller ran before calling this.
        Long userId = userService.getCurrentUser().getId();

        // 1️⃣ create query embedding
        var embedding = embeddingService.createQueryEmbedding(question);

        // 2️⃣ retrieve relevant chunks
        var chunks = pineconeService.search("chatbot_" + chatbotId, embedding);

        // 3️⃣ build context
        String context = String.join("\n", chunks);

        // 4️⃣ build the system instructions (context goes here, not in the
        // user turn, so prior turns in the conversation don't carry stale
        // retrieved context around with them)
        String systemPrompt = """
                You are an AI assistant.

                Use ONLY the context below.
                If the question related to something little in the context then please answer that also.

                Context:
                %s
                """.formatted(context);

        // 5️⃣ assemble messages: system instructions -> prior turns for this
        // chatbot (if any, within the retention window) -> the new question
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage("system", systemPrompt));
        messages.addAll(conversationMemoryService.getHistory(chatbotId));
        messages.add(new LLMMessage("user", question));

        // 6️⃣ generate answer
       LLMResponse response = llmClient.generate(
        LLMRequestDO.builder()
                .messages(messages)
                .build()
);

        // 7️⃣ remember this turn for the next question to this chatbot
        conversationMemoryService.appendTurn(
                chatbotId,
                userId,
                question,
                response.getAnswer(),
                response.getModel(),
                response.getTotalTokens(),
                response.getLatency());

return ChatResponseDTO.builder()
        .answer(response.getAnswer())
        .model(response.getModel())
        .promptTokens(response.getPromptTokens())
        .completionTokens(response.getCompletionTokens())
        .totalTokens(response.getTotalTokens())
        .latency(response.getLatency())
        .createdAt(java.time.LocalDateTime.now())
        .build();
    }
}
