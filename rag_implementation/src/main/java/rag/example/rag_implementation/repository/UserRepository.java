package rag.example.rag_implementation.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import rag.example.rag_implementation.mapper.UserRowMapper;
import rag.example.rag_implementation.model.User;

import java.util.List;

@Repository
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<User> getAllUsers() {

        String sql = "SELECT * FROM users";

        return jdbcTemplate.query(sql, new UserRowMapper());
    }

    public User findByEmail(String email) {

        String sql = "SELECT * FROM users WHERE email = :email";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email);

        List<User> users = jdbcTemplate.query(sql, params, new UserRowMapper());

        return users.isEmpty() ? null : users.get(0);
    }

    public int saveUser(String email, String password) {

        String sql = """
                INSERT INTO users(email,password)
                VALUES(:email,:password)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("password", password);

        return jdbcTemplate.update(sql, params);
    }

}