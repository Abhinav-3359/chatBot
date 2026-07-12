package rag.example.rag_implementation.mapper;

import org.springframework.jdbc.core.RowMapper;
import rag.example.rag_implementation.model.DocumentDO;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DocumentMapper implements RowMapper<DocumentDO> {
    @Override
    public DocumentDO mapRow(ResultSet rs, int rowNum) throws SQLException {
        DocumentDO document = new DocumentDO();
        document.setId(rs.getLong("id"));
        document.setChatbotId(rs.getLong("chatbot_id"));
        document.setDocumentName(rs.getString("file_name"));
        document.setDocumentType(rs.getString("file_type"));
        return document;
    }

}
