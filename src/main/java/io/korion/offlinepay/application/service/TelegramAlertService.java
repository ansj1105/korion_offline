package io.korion.offlinepay.application.service;

import io.korion.offlinepay.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class TelegramAlertService {

    private final RestClient restClient;
    private final AppProperties properties;

    public TelegramAlertService(RestClient restClient, AppProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public void notifyCircuitOpened(String serviceName, String reason) {
        send("[KORION] " + serviceName + " circuit opened\nreason=" + normalize(reason));
    }

    public void notifyCircuitRecovered(String serviceName) {
        send("[KORION] " + serviceName + " circuit recovered");
    }

    public void notifyDeadLetter(String serviceName, String reason) {
        send("[KORION] " + serviceName + " dead letter\nreason=" + normalize(reason));
    }

    public void notifyOperationalIssue(String serviceName, String reason) {
        send("[KORION] " + serviceName + " operational issue\nreason=" + normalize(reason));
    }

    public void notifyClientTrace(String summary, String filename, String jsonPayload) {
        AppProperties.Alerts alerts = properties.alerts();
        if (alerts == null || alerts.telegram() == null) {
            return;
        }
        String botToken = alerts.telegram().botToken();
        String chatId = alerts.telegram().chatId();
        if (isBlank(botToken) || isBlank(chatId)) {
            return;
        }

        byte[] bytes = normalize(jsonPayload).getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return isBlank(filename) ? "offline-pay-client-trace.json" : filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("caption", truncate(normalize(summary), 900));
        body.add("document", resource);
        restClient.post()
                .uri("/bot{token}/sendDocument", botToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void send(String text) {
        AppProperties.Alerts alerts = properties.alerts();
        if (alerts == null || alerts.telegram() == null) {
            return;
        }
        String botToken = alerts.telegram().botToken();
        String chatId = alerts.telegram().chatId();
        if (isBlank(botToken) || isBlank(chatId)) {
            return;
        }
        restClient.post()
                .uri("/bot{token}/sendMessage", botToken)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    private String normalize(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
