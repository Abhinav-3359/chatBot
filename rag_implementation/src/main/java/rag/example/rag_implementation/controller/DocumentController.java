package rag.example.rag_implementation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import rag.example.rag_implementation.services.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import rag.example.rag_implementation.common.ApiResponse;
import rag.example.rag_implementation.dto.DocumentResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/add/{chatbotId}")
    public ResponseEntity<ApiResponse<Integer>> addDocument(
            @PathVariable Long chatbotId,
            @RequestParam("file") MultipartFile file) {

        Integer documentId =
                documentService.processDocument(chatbotId, file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Document uploaded successfully",
                                documentId
                        )
                );
    }

    @GetMapping("/{chatbotId}")
    public ResponseEntity<ApiResponse<List<DocumentResponseDTO>>> getDocuments(
            @PathVariable Long chatbotId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Documents retrieved successfully",
                        documentService.getDocuments(chatbotId)
                )
        );
    }

    @DeleteMapping("/{chatbotId}/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable Long chatbotId,
            @PathVariable Long documentId) {

        documentService.deleteDocument(chatbotId, documentId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document deleted successfully",
                        null
                )
        );
    }
}