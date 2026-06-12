package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonService {

    private final ObjectMapper objectMapper;

    public JsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize json payload", exception);
        }
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to parse json payload", exception);
        }
    }

    public JsonNode valueToTree(Object value) {
        return objectMapper.valueToTree(value == null ? java.util.Map.of() : value);
    }

    public Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to parse json payload", exception);
        }
    }
}
