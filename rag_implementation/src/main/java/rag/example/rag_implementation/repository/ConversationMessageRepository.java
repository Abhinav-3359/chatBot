package rag.example.rag_implementation.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import rag.example.rag_implementation.mapper.ConversationMessageRowMapper;
import rag.example.rag_implementation.model.ConversationMessageDO;

import java.util.List;

@Repository
public class ConversationMessageRepository {

    // Messages older than this are treated as a dead conversation: left
    // out of both the LLM's context and what's shown in the UI. Keeps
    // old, abandoned chats from silently resurfacing later.
    private static final int RETENTION_HOURS = 6;

    // Safety net on top of the time window, in case one session is
    // unusually chatty within those 6 hours.
    private static final int MAX_MESSAGES = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConversationMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveMessage(
            Long chatbotId,
            Long userId,
            String role,
            String content,
            String model,
            Integer totalTokens,
            Long latencyMs) {

        String sql = """
                INSERT INTO conversation_messages
                    (chatbot_id, user_id, role, content, model, total_tokens, latency_ms)
                VALUES
                    (:chatbotId, :userId, :role, :content, :model, :totalTokens, :latencyMs)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("chatbotId", chatbotId)
                .addValue("userId", userId)
                .addValue("role", role)
                .addValue("content", content)
                .addValue("model", model)
                .addValue("totalTokens", totalTokens)
                .addValue("latencyMs", latencyMs);

        jdbcTemplate.update(sql, params);
    }

    public List<ConversationMessageDO> getRecentMessages(Long chatbotId) {

        String sql = """
                SELECT * FROM conversation_messages
                WHERE chatbot_id = :chatbotId
                  AND created_at > :cutoff
                ORDER BY id DESC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("chatbotId", chatbotId)
                .addValue("cutoff", java.time.LocalDateTime.now().minusHours(RETENTION_HOURS))
                .addValue("limit", MAX_MESSAGES);

        List<ConversationMessageDO> messages =
                jdbcTemplate.query(sql, params, new ConversationMessageRowMapper());

        // Query comes back newest-first (so LIMIT keeps the most recent
        // ones); flip it to chronological order for display/LLM context.
        java.util.Collections.reverse(messages);

        return messages;
    }

    /**
     * Physically deletes anything past the retention window. Called on a
     * schedule (see ConversationCleanupScheduler) - getRecentMessages
     * already excludes expired rows on its own, so this is purely about
     * reclaiming storage, not correctness.
     */
    public int deleteExpiredMessages() {

        String sql = "DELETE FROM conversation_messages WHERE created_at <= :cutoff";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", java.time.LocalDateTime.now().minusHours(RETENTION_HOURS));

        return jdbcTemplate.update(sql, params);
    }
}
