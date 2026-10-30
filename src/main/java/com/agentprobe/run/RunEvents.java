package com.agentprobe.run;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory SSE hub: one list of subscribers per run. */
@Component
public class RunEvents {

    private static final long TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<UUID, List<SseEmitter>>();

    public SseEmitter subscribe(UUID runId, Object snapshot) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<SseEmitter>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        if (!send(emitter, "snapshot", snapshot)) {
            list.remove(emitter);
        }
        return emitter;
    }

    public void publish(UUID runId, String name, Object data) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null) {
            return;
        }
        for (SseEmitter e : list) {
            if (!send(e, name, data)) {
                list.remove(e);
            }
        }
    }

    public void complete(UUID runId) {
        List<SseEmitter> list = emitters.remove(runId);
        if (list == null) {
            return;
        }
        for (SseEmitter e : list) {
            try {
                e.complete();
            } catch (RuntimeException ignored) {
                // already closed
            }
        }
    }

    private static boolean send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }
}