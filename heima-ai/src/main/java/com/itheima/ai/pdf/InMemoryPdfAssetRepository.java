package com.itheima.ai.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryPdfAssetRepository implements PdfAssetRepository {

    private final ObjectMapper objectMapper;

    // chatId -> assetMeta（bytes不放内存时也行；这里为了简单放内存）
    private final Map<String, PdfAsset> store = new HashMap<>();

    private static final String META_FILE = "pdf-assets.json";
    private static final String PDF_DIR = "pdf-store";

    @Override
    public void save(PdfAsset asset) {
        store.put(asset.getChatId(), asset);
    }

    @Override
    public Optional<PdfAsset> findByChatId(String chatId) {
        return Optional.ofNullable(store.get(chatId));
    }

    @Override
    public boolean exists(String chatId) {
        return store.containsKey(chatId);
    }


    //启动生成
    @PostConstruct
    private void init() {
        try {
            Files.createDirectories(Path.of(PDF_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create pdf-store dir", e);
        }

        FileSystemResource meta = new FileSystemResource(META_FILE);
        if (!meta.exists()) return;

        try {
            // 1) 先恢复 meta（不含 pdfBytes）
            Map<String, Map<String, Object>> metaMap =
                    objectMapper.readValue(meta.getInputStream(), new TypeReference<>() {});

            for (var entry : metaMap.entrySet()) {
                String chatId = entry.getKey();
                Map<String, Object> m = entry.getValue();

                String fileName = (String) m.getOrDefault("fileName", "document.pdf");
                String contentType = (String) m.getOrDefault("contentType", "application/pdf");
                String pdfText = (String) m.getOrDefault("pdfText", "");
                long uploadTime = ((Number) m.getOrDefault("uploadTime", 0)).longValue();

                // 2) 再恢复 pdfBytes
                Path pdfPath = Path.of(PDF_DIR, chatId + ".pdf");
                byte[] bytes = Files.exists(pdfPath) ? Files.readAllBytes(pdfPath) : null;

                if (bytes != null) {
                    store.put(chatId, new PdfAsset(chatId, fileName, contentType, bytes, pdfText, uploadTime));
                }
            }

            log.info("Loaded {} pdf assets from disk.", store.size());
        } catch (Exception e) {
            log.error("Failed to init pdf assets", e);
            // 不要影响启动，可按需要改为 throw
        }
    }


    //关闭启动结束时生成
    @PreDestroy
    private void persistent() {
        try {
            Files.createDirectories(Path.of(PDF_DIR));

            // 1) 保存 pdfBytes 到文件
            for (PdfAsset asset : store.values()) {
                if (asset.getPdfBytes() == null) continue;
                Path pdfPath = Path.of(PDF_DIR, asset.getChatId() + ".pdf");
                Files.write(pdfPath, asset.getPdfBytes());
            }

            // 2) 保存 meta 到 json（不放 bytes）
            Map<String, Map<String, Object>> metaMap = new HashMap<>();
            for (PdfAsset asset : store.values()) {
                Map<String, Object> m = new HashMap<>();
                m.put("fileName", asset.getFileName());
                m.put("contentType", asset.getContentType());
                m.put("pdfText", asset.getPdfText());
                m.put("uploadTime", asset.getUploadTime());
                metaMap.put(asset.getChatId(), m);
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metaMap);
            FileSystemResource meta = new FileSystemResource(META_FILE);

            try (PrintWriter w = new PrintWriter(meta.getOutputStream(), true, StandardCharsets.UTF_8)) {
                w.write(json);
            }

            log.info("Persisted pdf assets: {}", store.size());
        } catch (Exception e) {
            log.error("Failed to persist pdf assets", e);
        }
    }
}
