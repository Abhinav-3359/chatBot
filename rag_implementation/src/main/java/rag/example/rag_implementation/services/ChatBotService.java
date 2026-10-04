package rag.example.rag_implementation.services;

import org.springframework.stereotype.Service;
import rag.example.rag_implementation.exception.ChatBotAccessDeniedException;
import rag.example.rag_implementation.exception.ChatBotNotFoundException;
import rag.example.rag_implementation.model.ChatBotDO;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.repository.ChatBotRepository;

import java.util.List;

@Service
public class ChatBotService {

    private final ChatBotRepository chatBotRepository;
    private final UserService userService;

    public ChatBotService(ChatBotRepository chatBotRepository, UserService userService) {
        this.chatBotRepository = chatBotRepository;
        this.userService = userService;
    }

    public List<ChatBotDO> getAllChatBots() {
        User currentUser = userService.getCurrentUser();
        return chatBotRepository.getAllChatBotsByUserId(currentUser.getId());
    }

    public Integer createChatBot(ChatBotDO chatBot) {

        // Always the caller's own id, regardless of whatever userId (if
        // any) was sent in the request body - otherwise a client could
        // create a chatbot "owned" by someone else.
        User currentUser = userService.getCurrentUser();
        chatBot.setUserId(currentUser.getId());

        return chatBotRepository.saveChatBot(chatBot);
    }

    public ChatBotDO getChatBotById(Long id) {

        ChatBotDO chatBot = chatBotRepository.getChatBotById(id);

        if (chatBot == null) {
            throw new ChatBotNotFoundException("Chatbot not found.");
        }

        return chatBot;
    }

    /**
     * Same as getChatBotById, but also enforces that the chatbot belongs
     * to the currently authenticated user. Use this (not getChatBotById)
     * for any externally-reachable path that acts on a specific chatbot -
     * reading, updating, uploading documents to it, or asking it a
     * question - so one user can't touch another user's chatbot.
     */
    public ChatBotDO getOwnedChatBot(Long id) {

        ChatBotDO chatBot = getChatBotById(id);
        User currentUser = userService.getCurrentUser();

        if (!chatBot.getUserId().equals(currentUser.getId())) {
            throw new ChatBotAccessDeniedException(
                    "You do not have access to this chatbot.");
        }

        return chatBot;
    }

    public ChatBotDO updateChatBot(Long id, ChatBotDO request) {

        ChatBotDO existingChatBot = getOwnedChatBot(id);

        if (request.getName() != null) {
            existingChatBot.setName(request.getName());
        }

        if (request.getDescription() != null) {
            existingChatBot.setDescription(request.getDescription());
        }

        if (request.getPineConeNamespace() != null) {
            existingChatBot.setPineConeNamespace(request.getPineConeNamespace());
        }

        chatBotRepository.updateChatBot(existingChatBot);

        return chatBotRepository.getChatBotById(id);
    }
}