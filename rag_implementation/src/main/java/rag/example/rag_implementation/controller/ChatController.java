package rag.example.rag_implementation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import rag.example.rag_implementation.services.ChatBotService;
import rag.example.rag_implementation.services.ConversationMemoryService;
import rag.example.rag_implementation.services.RagService;
import rag.example.rag_implementation.common.ApiResponse;
import rag.example.rag_implementation.dto.ChatRequestDTO;
import rag.example.rag_implementation.dto.ChatResponseDTO;
import rag.example.rag_implementation.dto.ConversationMessageDTO;
import rag.example.rag_implementation.dto.VoiceQueryRequest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/chatbots")
public class ChatController {

    private final RagService ragService;
    private final ChatBotService chatBotService;
    private final ConversationMemoryService conversationMemoryService;

    public ChatController(
            RagService ragService,
            ChatBotService chatBotService,
            ConversationMemoryService conversationMemoryService) {

        this.ragService = ragService;
        this.chatBotService = chatBotService;
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<ApiResponse<ChatResponseDTO>> chat(
            @PathVariable Long id,
            @RequestBody ChatRequestDTO request) {

        // Throws 403 if this chatbot isn't the caller's own.
        chatBotService.getOwnedChatBot(id);

        ChatResponseDTO response =
                ragService.askQuestion(id, request.getMessage());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Response generated successfully",
                        response
                )
        );
    }

    @GetMapping("/{id}/chat/history")
    public ResponseEntity<ApiResponse<List<ConversationMessageDTO>>> getChatHistory(
            @PathVariable Long id) {

        // Throws 403 if this chatbot isn't the caller's own.
        chatBotService.getOwnedChatBot(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chat history retrieved successfully",
                        conversationMemoryService.getDisplayHistory(id)
                )
        );
    }

@PostMapping("/{id}/voice-query")
public ResponseEntity<ApiResponse> voiceQuery(
        @PathVariable Long id,
        @RequestBody VoiceQueryRequest request) {

    System.out.println("VOICE QUERY HIT -> chatbotId=" + id +
            ", text=" + request.getText());

    chatBotService.getOwnedChatBot(id);

    ChatResponseDTO response =
            ragService.askQuestion(id, request.getText());

    return ResponseEntity.ok(
            ApiResponse.success(
                    "Voice response generated successfully",
                    response
            )
    );
}
}