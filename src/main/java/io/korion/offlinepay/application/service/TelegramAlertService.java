package io.korion.offlinepay.application.service;

import io.korion.offlinepay.config.AppProperties;
import java.util.Map;
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
}
