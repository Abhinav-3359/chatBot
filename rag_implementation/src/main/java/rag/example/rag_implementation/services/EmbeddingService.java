package rag.example.rag_implementation.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();

    // used for document chunks
    public List<Double> createEmbedding(String text) {

        String url = "http://localhost:9000/embed";

        Map<String, String> request = Map.of("text", text);

        Map response = restTemplate.postForObject(url, request, Map.class);

        return (List<Double>) response.get("embedding");
    }

    // used for user queries
    public List<Double> createQueryEmbedding(String text) {

        String url = "http://localhost:9000/embed_query";

        Map<String, String> request = Map.of("text", text);

        Map response = restTemplate.postForObject(url, request, Map.class);

        return (List<Double>) response.get("embedding");
    }
}