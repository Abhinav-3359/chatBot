package rag.example.rag_implementation.mapper;

import org.springframework.jdbc.core.RowMapper;
import rag.example.rag_implementation.model.ChatBotDO;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatBotRowMapper implements RowMapper<ChatBotDO> {

    @Override
    public ChatBotDO mapRow(ResultSet rs, int rowNum) throws SQLException {

        ChatBotDO chatBot = new ChatBotDO();

        chatBot.setId(rs.getLong("id"));
        chatBot.setUserId(rs.getLong("user_id"));
        chatBot.setName(rs.getString("name"));
        chatBot.setDescription(rs.getString("description"));
        chatBot.setPineConeNamespace(rs.getString("pinecone_namespace"));

        return chatBot;
    }

}
