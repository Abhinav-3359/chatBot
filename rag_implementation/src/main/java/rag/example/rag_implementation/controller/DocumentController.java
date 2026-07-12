package rag.example.rag_implementation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import rag.example.rag_implementation.services.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    @Autowired
    DocumentService documentService;

    @PostMapping("/add/{chatbotId}")
    public Integer addDocument(@PathVariable Long chatbotId,
            @RequestParam("file") MultipartFile file) {
        return documentService.processDocument(chatbotId, file);
    }

}