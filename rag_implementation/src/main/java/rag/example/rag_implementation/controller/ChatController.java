package rag.example.rag_implementation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import rag.example.rag_implementation.dto.chatRequestDTO;
import rag.example.rag_implementation.services.RagService;

@RestController
@RequestMapping("/chatbots")
public class ChatController {

    @Autowired
    private RagService ragService;

    @PostMapping("/{id}/chat")
    public String chat(
            @PathVariable Long id,
            @RequestBody chatRequestDTO request) {

        return ragService.askQuestion(
                id,
                request.getMessage());
    }
}