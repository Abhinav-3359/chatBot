package rag.example.rag_implementation.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rag.example.rag_implementation.repository.ConversationMessageRepository;

/**
 * Physically purges conversation_messages rows past the 6-hour retention
 * window. ConversationMessageRepository.getRecentMessages() already
 * excludes expired rows from every read (LLM context and chat history
 * display), so this is purely housekeeping - it keeps the table from
 * growing unbounded, it doesn't affect correctness if it runs late.
 */
@Component
public class ConversationCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ConversationCleanupScheduler.class);

    private final ConversationMessageRepository conversationMessageRepository;

    public ConversationCleanupScheduler(ConversationMessageRepository conversationMessageRepository) {
        this.conversationMessageRepository = conversationMessageRepository;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void purgeExpiredMessages() {

        int deleted = conversationMessageRepository.deleteExpiredMessages();

        if (deleted > 0) {
            logger.info("Purged {} expired conversation message(s).", deleted);
        }
    }
}
