package rag.example.rag_implementation.services;

import java.util.ArrayList;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import rag.example.rag_implementation.dto.DocumentResponseDTO;
import rag.example.rag_implementation.exception.DocumentNotFoundException;
import rag.example.rag_implementation.model.DocumentDO;
import rag.example.rag_implementation.repository.DocumentRepository;
import rag.example.rag_implementation.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

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

    @Autowired
    ChatBotService chatBotService;

    Logger log = (Logger) LoggerFactory.getLogger(DocumentService.class);

    public List<DocumentResponseDTO> getDocuments(Long chatbotId) {

        // Throws 403 if this chatbot isn't the caller's own.
        chatBotService.getOwnedChatBot(chatbotId);

        return documentRepository.getDocumentsByChatbotId(chatbotId).stream()
                .map(doc -> DocumentResponseDTO.builder()
                        .id(doc.getId())
                        .documentName(doc.getDocumentName())
                        .documentType(doc.getDocumentType())
                        .chunkCount(chunkRepository.countByDocumentId(doc.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    public void deleteDocument(Long chatbotId, Long documentId) {

        // Throws 403 if this chatbot isn't the caller's own.
        chatBotService.getOwnedChatBot(chatbotId);

        DocumentDO document = documentRepository.getDocumentById(documentId);

        if (document == null || document.getChatbotId() != chatbotId) {
            throw new DocumentNotFoundException("Document not found.");
        }

        List<String> vectorIds = chunkRepository.getVectorIdsByDocumentId(documentId);

        pineconeIntegrationService.deleteVectors("chatbot_" + chatbotId, vectorIds);

        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteDocument(documentId);
    }

    public Integer processDocument(Long chatbotId, MultipartFile file) {

        // Fail fast if the caller doesn't own this chatbot, before paying
        // for Tika extraction / embedding / Pinecone upserts.
        chatBotService.getOwnedChatBot(chatbotId);

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
    log.info("Pinecone upserted");

    log.info("Saving chunk metadata...");
    chunkRepository.saveChunk(
            documentId,
            index,
            vectorId);
    log.info("Chunk metadata saved");
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
