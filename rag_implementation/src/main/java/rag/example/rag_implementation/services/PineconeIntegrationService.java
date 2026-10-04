package rag.example.rag_implementation.services;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PineconeIntegrationService {
    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.index-url}")
    private String indexUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public void upsertVector(
            String namespace,
            String vectorId,
            List<Double> embedding,
            String text) {

        String url = indexUrl + "/vectors/upsert";

        Map<String, Object> metadata = Map.of(
                "text", text);

        Map<String, Object> vector = Map.of(
                "id", vectorId,
                "values", embedding,
                "metadata", metadata);

        Map<String, Object> request = Map.of(
                "vectors", List.of(vector),
                "namespace", namespace);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        restTemplate.postForObject(url, entity, String.class);
    }

    public void deleteVectors(String namespace, List<String> vectorIds) {

        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }

        String url = indexUrl + "/vectors/delete";

        Map<String, Object> request = Map.of(
                "ids", vectorIds,
                "namespace", namespace);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        restTemplate.postForObject(url, entity, String.class);
    }

    public List<String> search(String namespace, List<Double> embedding) {

        String url = indexUrl + "/query";

        Map<String, Object> request = Map.of(
                "vector", embedding,
                "topK", 3,
                "includeMetadata", true,
                "namespace", namespace);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        Map response = restTemplate.postForObject(url, entity, Map.class);

        List<String> chunks = new ArrayList<>();

        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.get("matches");

        if (matches != null) {

            for (Map<String, Object> match : matches) {

                Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");

                if (metadata != null) {
                    chunks.add((String) metadata.get("text"));
                }
            }
        }

        return chunks;
    }
}
