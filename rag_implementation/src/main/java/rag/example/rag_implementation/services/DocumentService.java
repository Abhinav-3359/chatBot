package rag.example.rag_implementation.services;

import java.util.ArrayList;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import rag.example.rag_implementation.model.DocumentDO;
import rag.example.rag_implementation.repository.DocumentRepository;
import rag.example.rag_implementation.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentService {
    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    ChunkRepository chunkRepository;
    @Autowired
    EmbeddingService embeddingService;
    @Autowired
    PineconeIntegrationService pineconeIntegrationService;

    Logger log = (Logger) LoggerFactory.getLogger(DocumentService.class);

    public Integer processDocument(Long chatbotId, MultipartFile file) {
        String text = extractText(file);
        log.info("text extracted");
        Integer documentId = documentRepository.saveDocument(
                chatbotId,
                file.getOriginalFilename(),
                file.getContentType());

        List<String> chunks = chunkText(text);

        int index = 0;

        for (String chunk : chunks) {

            List<Double> embedding = embeddingService.createEmbedding(chunk);
            String vectorId = java.util.UUID.randomUUID().toString();
            pineconeIntegrationService.upsertVector(
                    "chatbot_" + chatbotId,
                    vectorId,
                    embedding,
                    chunk);
            chunkRepository.saveChunk(
                    documentId,
                    index,
                    vectorId);

            index++;
        }
        return documentId;
    }

    private String extractText(MultipartFile file) {

        try {

            Tika tika = new Tika();
            log.info("text extraction begins");
            return tika.parseToString(file.getInputStream());

        } catch (Exception e) {
            log.error("Failed to parse file", e);
            throw new RuntimeException("Failed to parse file");
        }
    }

    private List<String> chunkText(String text) {

        int chunkSize = 500;
        int overlap = 100;

        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < text.length(); i += (chunkSize - overlap)) {

            int end = Math.min(i + chunkSize, text.length());

            chunks.add(text.substring(i, end));

            if (end == text.length()) {
                break;
            }
        }

        return chunks;
    }

}
