package rag.example.rag_implementation.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rag.example.rag_implementation.model.ChatBotDO;
import rag.example.rag_implementation.repository.ChatBotRepository;
import java.util.*;

@Service
public class ChatBotService {
    @Autowired
    ChatBotRepository chatBotRepository;

    public List<ChatBotDO> getAllChatBots() {
        return chatBotRepository.getAllChatBots();
    }

    public Integer createChatBot(ChatBotDO chatBot) {
        Integer result = chatBotRepository.saveChatBot(chatBot);
        if (result != null) {
            return result;
        }
        return 0;
    }

    public ChatBotDO getChatBotById(Long id) {
        return chatBotRepository.getChatBotById(id);
    }

    public int updateChatBot(ChatBotDO chatBot) {
        return chatBotRepository.updateChatBot(chatBot);
    }
}
