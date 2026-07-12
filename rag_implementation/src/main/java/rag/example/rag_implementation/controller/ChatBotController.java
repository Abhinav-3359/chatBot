package rag.example.rag_implementation.controller;

import org.springframework.web.bind.annotation.*;
import rag.example.rag_implementation.model.ChatBotDO;
import rag.example.rag_implementation.services.ChatBotService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/chatbots")
public class ChatBotController {
    @Autowired
    private ChatBotService chatBotService;

    @GetMapping
    public java.util.List<ChatBotDO> getAllChatBots() {
        return chatBotService.getAllChatBots();
    }

    @PostMapping("/add")
    public Integer createChatBot(@RequestBody ChatBotDO chatBot) {
        return chatBotService.createChatBot(chatBot);
    }

    @GetMapping("/{id}")
    public ChatBotDO getChatBotById(@PathVariable Long id) {
        return chatBotService.getChatBotById(id);
    }

    @PostMapping("/update/{id}")
    public ChatBotDO updateChatBot(@PathVariable Long id, @RequestBody ChatBotDO chatBot) {
        try {
            ChatBotDO existingChatBot = chatBotService.getChatBotById(id);
            ChatBotDO updatedChatBot = existingChatBot;
            updatedChatBot.setId(existingChatBot.getId());
            updatedChatBot.setUserId(existingChatBot.getUserId());
            if (chatBot.getName() != null) {
                updatedChatBot.setName(chatBot.getName());
            }
            if (chatBot.getDescription() != null) {
                updatedChatBot.setDescription(chatBot.getDescription());
            }
            if (chatBot.getPineConeNamespace() != null) {
                updatedChatBot.setPineConeNamespace(chatBot.getPineConeNamespace());
            }
            chatBotService.updateChatBot(updatedChatBot);
            return chatBotService.getChatBotById(id);
        } catch (Exception e) {

            return null;
        }

    }

}
