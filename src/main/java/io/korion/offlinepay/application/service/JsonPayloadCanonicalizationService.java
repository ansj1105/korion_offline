package io.korion.offlinepay.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class JsonPayloadCanonicalizationService {

    private final JsonService jsonService;

    public JsonPayloadCanonicalizationService(JsonService jsonService) {
        this.jsonService = jsonService;
    }

    public String canonicalize(String json) {
        return jsonService.write(canonicalizeNode(jsonService.readTree(json)));
    }

    public boolean sameJson(String left, String right) {
        if (isBlank(left) || isBlank(right)) {
            return false;
        }
        return canonicalize(left).equals(canonicalize(right));
    }

    private JsonNode canonicalizeNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                fields.put(entry.getKey(), canonicalizeNode(entry.getValue()));
            }
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(canonicalizeNode(item)));
            return array;
        }
        return node;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
