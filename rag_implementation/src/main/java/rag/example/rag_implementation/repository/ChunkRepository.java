package rag.example.rag_implementation.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ChunkRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveChunk(
            Integer documentId,
            Integer chunkIndex,
            String vectorId) {

        String sql = """
                INSERT INTO document_chunks(document_id, chunk_index, vector_id)
                VALUES(:documentId, :chunkIndex, :vectorId)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("documentId", documentId)
                .addValue("chunkIndex", chunkIndex)
                .addValue("vectorId", vectorId);

        jdbcTemplate.update(sql, params);
    }

    public int countByDocumentId(long documentId) {
        String sql = "SELECT COUNT(*) FROM document_chunks WHERE document_id = :documentId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("documentId", documentId);
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count == null ? 0 : count;
    }

    public java.util.List<String> getVectorIdsByDocumentId(long documentId) {
        String sql = "SELECT vector_id FROM document_chunks WHERE document_id = :documentId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("documentId", documentId);
        return jdbcTemplate.queryForList(sql, params, String.class);
    }

    public void deleteByDocumentId(long documentId) {
        String sql = "DELETE FROM document_chunks WHERE document_id = :documentId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("documentId", documentId);
        jdbcTemplate.update(sql, params);
    }
}