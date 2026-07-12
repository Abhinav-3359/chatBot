package rag.example.rag_implementation.services;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LLMService {
    private final RestTemplate restTemplate = new RestTemplate();

    public String generateAnswer(String prompt) {

        String url = "http://localhost:11434/api/generate";

        Map<String, Object> request = Map.of(
                "model", "gemma:2b",
                "prompt", prompt,
                "stream", false);

        Map response = restTemplate.postForObject(url, request, Map.class);

        return (String) response.get("response");
    }

}
