package rag.example.rag_implementation.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import rag.example.rag_implementation.mapper.DocumentMapper;
import rag.example.rag_implementation.model.DocumentDO;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.util.List;

@Repository
public class DocumentRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentDO> getAllDocuments() {
        String sql = "SELECT * FROM documents";
        return jdbcTemplate.query(sql, new DocumentMapper());
    }

    public Integer saveDocument(Long chatbotId, String documentName, String documentType) {
        try {
            String sql = "INSERT INTO documents (chatbot_id, file_name, file_type) VALUES (:chatbotId, :documentName, :documentType)";
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("chatbotId", chatbotId)
                    .addValue("documentName", documentName)
                    .addValue("documentType", documentType);

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(sql, params, keyHolder, new String[] { "id" });
            return keyHolder.getKey().intValue();
        } catch (Exception e) {
            throw e;
        }
    }
}