package com.itheima.ai.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PdfRagService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;



    /**
     * 1) 将 pdfText 切分为 chunks
     * 2) chunks 向量化入库（带 metadata: chatId/fileName/chunkIndex/uploadTime）
     */
    public void indexPdf(String chatId, String fileName, String pdfText, long uploadTime) {
        if (pdfText == null || pdfText.isBlank()) return;

        // 切分策略：chunkSize / overlap 可按你模型上下文调整
        TokenTextSplitter splitter = new TokenTextSplitter(800, 200, 20, 2000, true);
        List<Document> docs = splitter.apply(List.of(new Document(pdfText)));

        List<Document> toStore = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);

            Map<String, Object> meta = new HashMap<>();
            meta.put("chatId", chatId);
            meta.put("fileName", fileName);
            meta.put("chunkIndex", i);
            meta.put("uploadTime", uploadTime);

            // 重新构造，确保 metadata 完整
            toStore.add(new Document(d.getText(), meta));
        }

        // 写入向量库（内部会自动调用 embeddingModel 生成向量）
        vectorStore.add(toStore);
    }

    /**
     * 根据 prompt 检索 topK chunks（限定 chatId）
     */
    public List<Document> retrieveTopK(String chatId, String prompt, int topK) {
        // SearchRequest 支持 filter（不同向量库实现 filter 语法略不同）
        // RedisVectorStore/PGVectorStore 通常支持 metadata 过滤
        SearchRequest req = SearchRequest.builder()
                .query(prompt)
                .topK(topK)
                .filterExpression("chatId == '" + chatId + "'") // 仅检索当前 chatId 的 chunks
                .build();

        return vectorStore.similaritySearch(req);
    }
}
