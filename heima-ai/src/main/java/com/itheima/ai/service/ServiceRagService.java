package com.itheima.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceRagService {

    private final ServiceKbRepository kbRepository;

    public List<ServiceKbItem> retrieveTopK(String prompt, int topK) {
        if (prompt == null || prompt.isBlank()) return List.of();

        String q = prompt.toLowerCase(Locale.ROOT);

        // 简单评分：title/tags/content 命中加分
        record Scored(ServiceKbItem item, int score) {}
        List<Scored> scored = new ArrayList<>();

        for (ServiceKbItem item : kbRepository.listAll()) {
            int s = 0;
            if (item.getTitle() != null && item.getTitle().toLowerCase(Locale.ROOT).contains(q)) s += 3;
            if (item.getContent() != null && item.getContent().toLowerCase(Locale.ROOT).contains(q)) s += 1;

            if (item.getTags() != null) {
                for (String tag : item.getTags()) {
                    if (tag == null) continue;
                    if (q.contains(tag.toLowerCase(Locale.ROOT))) s += 4;
                }
            }
            if (s > 0) scored.add(new Scored(item, s));
        }

        return scored.stream()
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(topK)
                .map(Scored::item)
                .collect(Collectors.toList());
    }

    public String buildContext(List<ServiceKbItem> items) {
        if (items == null || items.isEmpty()) return "（未检索到相关知识库片段）";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            ServiceKbItem it = items.get(i);
            sb.append("【知识片段 ").append(i + 1).append("】")
                    .append(it.getTitle() == null ? "" : it.getTitle())
                    .append("\n")
                    .append(it.getContent() == null ? "" : it.getContent())
                    .append("\n\n---\n\n");
        }
        return sb.toString();
    }
}
