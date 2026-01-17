package com.itheima.ai.Assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryChatMessageRepository implements ChatMessageRepository {

    private final ObjectMapper objectMapper;

    /**
     * type -> chatId -> messages   实现保存消息记录到messages-store.json，和展示历史记录的功能
     */
    private final Map<String, Map<String, List<ChatMessage>>> store = new HashMap<>();

    private static final String FILE = "messages-store.json";

    @Override
    public synchronized void append(String type, String chatId, ChatMessage message) {
        store.computeIfAbsent(type, k -> new HashMap<>())
                .computeIfAbsent(chatId, k -> new ArrayList<>())
                .add(message);
    }

    @Override
    public synchronized List<ChatMessage> list(String type, String chatId) {
        return new ArrayList<>(
                store.getOrDefault(type, Map.of())
                        .getOrDefault(chatId, List.of())
        );
    }

    @PostConstruct
    private void init() {
        FileSystemResource res = new FileSystemResource(FILE);
        if (!res.exists()) return;
        try {
            Map<String, Map<String, List<ChatMessage>>> data =
                    objectMapper.readValue(res.getInputStream(), new TypeReference<>() {});
            store.clear();
            store.putAll(data);
            log.info("Loaded chat messages from {}. types={}", FILE, store.keySet());
        } catch (Exception e) {
            log.error("Failed to load {}", FILE, e);
        }
    }

    @PreDestroy
    private void persist() {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(store);
            FileSystemResource res = new FileSystemResource(FILE);
            try (PrintWriter w = new PrintWriter(res.getOutputStream(), true, StandardCharsets.UTF_8)) {
                w.write(json);
            }
            log.info("Persisted chat messages to {}.", FILE);
        } catch (Exception e) {
            log.error("Failed to persist {}", FILE, e);
        }
    }
}
