package rag.example.rag_implementation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import rag.example.rag_implementation.common.ApiResponse;

import java.io.IOException;

/**
 * Without this, Spring Security has no configured entry point (this app
 * uses neither httpBasic() nor formLogin()), so it silently falls back to
 * Http403ForbiddenEntryPoint for every unauthenticated request - meaning
 * "you sent no/invalid token" and "you sent a valid token for a chatbot
 * you don't own" (ChatBotAccessDeniedException, handled separately in
 * GlobalExceptionHandler) were indistinguishable 403s. This makes the
 * "not authenticated at all" case a proper 401, matching normal REST
 * semantics and what the frontend's axios interceptor already expects
 * for "log the user out and send them to the login page".
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.failure("Authentication required.")
                )
        );
    }
}
