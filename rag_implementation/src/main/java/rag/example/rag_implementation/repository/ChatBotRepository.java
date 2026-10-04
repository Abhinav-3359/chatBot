package rag.example.rag_implementation.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import rag.example.rag_implementation.mapper.ChatBotRowMapper;
import rag.example.rag_implementation.model.ChatBotDO;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.util.List;

@Repository
public class ChatBotRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ChatBotRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChatBotDO> getAllChatBots() {
        String sql = "SELECT * FROM chatbots";
        return jdbcTemplate.query(sql, new ChatBotRowMapper());
    }

    public List<ChatBotDO> getAllChatBotsByUserId(Long userId) {
        String sql = "SELECT * FROM chatbots WHERE user_id = :userId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
        return jdbcTemplate.query(sql, params, new ChatBotRowMapper());
    }

    public Integer saveChatBot(ChatBotDO chatBot) {
        String sql = "INSERT INTO chatbots (user_id, name, description, pinecone_namespace) VALUES (:userId, :name, :description, :pineconeNamespace)";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", chatBot.getUserId())
                .addValue("name", chatBot.getName()).addValue("description", chatBot.getDescription())
                .addValue("pineconeNamespace", chatBot.getPineConeNamespace());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[] { "id" });
        return keyHolder.getKey().intValue();
    }

    public ChatBotDO getChatBotById(Long id) {
        String sql = "SELECT * FROM chatbots WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        List<ChatBotDO> chatBots = jdbcTemplate.query(sql, params, new ChatBotRowMapper());
        return chatBots.isEmpty() ? null : chatBots.get(0);
    }

    public void updateChatBot(ChatBotDO chatBot) {

            String sql = "UPDATE chatbots SET name = :name, description = :description, pinecone_namespace = :pineconeNamespace WHERE id = :id";
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", chatBot.getId())
                    .addValue("name", chatBot.getName()).addValue("description", chatBot.getDescription())
                    .addValue("pineconeNamespace", chatBot.getPineConeNamespace());
            jdbcTemplate.update(sql, params);
        
    }

}
