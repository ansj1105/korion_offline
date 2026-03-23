package io.korion.offlinepay.application.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class OfflineSnapshotStreamService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long userId, String deviceId) {
        SseEmitter emitter = new SseEmitter(0L);
        String key = subscriberKey(userId, deviceId);
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError((ignored) -> removeEmitter(key, emitter));

        sendHeartbeat(emitter);
        return emitter;
    }

    public void publishDeviceRegistrationChanged(long userId, String deviceId, String reason) {
        publish(userId, deviceId, new SnapshotEvent(
                UUID.randomUUID().toString(),
                "DEVICE_REGISTRATION_CHANGED",
                userId,
                deviceId,
                "",
                reason == null ? "" : reason,
                OffsetDateTime.now().toString()
        ));
    }

    public void publishCollateralChanged(long userId, String deviceId, String assetCode, String reason) {
        publish(userId, deviceId, new SnapshotEvent(
                UUID.randomUUID().toString(),
                "COLLATERAL_CHANGED",
                userId,
                deviceId,
                assetCode == null ? "" : assetCode,
                reason == null ? "" : reason,
                OffsetDateTime.now().toString()
        ));
        publish(userId, deviceId, new SnapshotEvent(
                UUID.randomUUID().toString(),
                "WALLET_REFRESH_REQUIRED",
                userId,
                deviceId,
                assetCode == null ? "" : assetCode,
                reason == null ? "" : reason,
                OffsetDateTime.now().toString()
        ));
    }

    private void publish(long userId, String deviceId, SnapshotEvent event) {
        List<SseEmitter> subscribers = emitters.get(subscriberKey(userId, deviceId));
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name("snapshot-change")
                        .id(event.eventId())
                        .data(event));
            } catch (IOException error) {
                emitter.completeWithError(error);
            }
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot-heartbeat")
                    .data(Map.of("status", "CONNECTED", "occurredAt", OffsetDateTime.now().toString())));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(key);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(key);
        }
    }

    private String subscriberKey(long userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    public record SnapshotEvent(
            String eventId,
            String type,
            long userId,
            String deviceId,
            String assetCode,
            String reason,
            String occurredAt
    ) {}
}
