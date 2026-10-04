package rag.example.rag_implementation.controller;

import org.springframework.web.bind.annotation.*;
import rag.example.rag_implementation.model.ChatBotDO;
import rag.example.rag_implementation.services.ChatBotService;
import rag.example.rag_implementation.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/chatbots")
public class ChatBotController {

    private final ChatBotService chatBotService;

    public ChatBotController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatBotDO>>> getAllChatBots() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chatbots retrieved successfully",
                        chatBotService.getAllChatBots()
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatBotDO>> getChatBotById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chatbot retrieved successfully",
                        chatBotService.getOwnedChatBot(id)
                )
        );
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Integer>> createChatBot(
            @RequestBody ChatBotDO chatBot) {

        Integer chatbotId =
                chatBotService.createChatBot(chatBot);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Chatbot created successfully",
                                chatbotId
                        )
                );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatBotDO>> updateChatBot(
            @PathVariable Long id,
            @RequestBody ChatBotDO chatBot) {

        ChatBotDO updated =
                chatBotService.updateChatBot(id, chatBot);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chatbot updated successfully",
                        updated
                )
        );
    }
}