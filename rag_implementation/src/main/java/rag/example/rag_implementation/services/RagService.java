package rag.example.rag_implementation.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rag.example.rag_implementation.services.PineconeIntegrationService;
import rag.example.rag_implementation.services.EmbeddingService;
import rag.example.rag_implementation.services.LLMService;

@Service
public class RagService {
    @Autowired
    private PineconeIntegrationService pineconeService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private LLMService llmService;

    public String askQuestion(Long chatbotId, String question) {

        // 1️⃣ create query embedding
        var embedding = embeddingService.createQueryEmbedding(question);

        // 2️⃣ retrieve relevant chunks
        var chunks = pineconeService.search("chatbot_" + chatbotId, embedding);

        // 3️⃣ build context
        String context = String.join("\n", chunks);

        // 4️⃣ build prompt
        String prompt = """
                You are an AI assistant.

                Use ONLY the context below.
                If answer is not among the context say "I don't know".

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

        // 5️⃣ generate answer
        return llmService.generateAnswer(prompt);
    }
}
