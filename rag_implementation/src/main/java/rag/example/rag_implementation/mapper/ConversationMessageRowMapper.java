package rag.example.rag_implementation.mapper;

import org.springframework.jdbc.core.RowMapper;
import rag.example.rag_implementation.model.ConversationMessageDO;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConversationMessageRowMapper implements RowMapper<ConversationMessageDO> {

    @Override
    public ConversationMessageDO mapRow(ResultSet rs, int rowNum) throws SQLException {

        ConversationMessageDO message = new ConversationMessageDO();

        message.setId(rs.getLong("id"));
        message.setChatbotId(rs.getLong("chatbot_id"));
        message.setUserId(rs.getLong("user_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setModel(rs.getString("model"));

        int totalTokens = rs.getInt("total_tokens");
        message.setTotalTokens(rs.wasNull() ? null : totalTokens);

        long latencyMs = rs.getLong("latency_ms");
        message.setLatencyMs(rs.wasNull() ? null : latencyMs);

        message.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

        return message;
    }
}
