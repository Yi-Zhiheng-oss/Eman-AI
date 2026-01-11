package com.itheima.ai.controller;

import com.itheima.ai.pdf.PdfAsset;
import com.itheima.ai.pdf.PdfAssetRepository;
import com.itheima.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfChatController {

    private final ChatClient chatClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final PdfAssetRepository pdfAssetRepository;

    /**
     * 前端：POST /ai/pdf/upload/{chatId}  body=FormData(file)
     * 返回：{ chatId: "...", fileName: "..." }
     */
//新聊天界面上传数据
    @PostMapping("/upload/{chatId}")
    public Map<String, Object> uploadPdf(@PathVariable("chatId") String chatId,
                                         @RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        if (!Objects.equals(file.getContentType(), "application/pdf")) {
            // 前端限制了 pdf，但后端也要校验
            throw new IllegalArgumentException("only application/pdf allowed");
        }

        byte[] pdfBytes = file.getBytes();
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "document.pdf";

        // 1) 抽取文本（PDFBox依赖）
        String pdfText = extractPdfText(pdfBytes);

        // 2) 保存资产  PdfAsset对象保存
        PdfAsset asset = new PdfAsset(
                chatId,
                fileName,
                "application/pdf",
                pdfBytes,
                pdfText,
                Instant.now().toEpochMilli()
        );
        pdfAssetRepository.save(asset);

        // 3) 保存会话ID到历史（type=pdf）
        chatHistoryRepository.save("pdf", chatId);

        // 4) 返回
        Map<String, Object> res = new HashMap<>();
        res.put("chatId", chatId);
        res.put("fileName", fileName);
        res.put("textLength", pdfText == null ? 0 : pdfText.length());
        return res;
    }

    /**
     * 前端：GET /ai/pdf/file/{chatId}
     * 用于历史会话加载时回显 PDF
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<ByteArrayResource> getPdfFile(@PathVariable("chatId") String chatId) {
        PdfAsset asset = pdfAssetRepository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalArgumentException("PDF not found for chatId=" + chatId));

        String encodedName = URLEncoder.encode(asset.getFileName(), StandardCharsets.UTF_8);

        ByteArrayResource resource = new ByteArrayResource(asset.getPdfBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedName + "\"")
                .contentLength(asset.getPdfBytes().length)
                .body(resource);
    }

    /**
     * 前端：GET /ai/pdf/chat?prompt=...&chatId=...
     * 返回：流式文本
     */
    @GetMapping(value = "/chat", produces = "text/plain;charset=utf-8")
    public Flux<String> chatPdf(@RequestParam("prompt") String prompt,
                                @RequestParam("chatId") String chatId) {

        PdfAsset asset = pdfAssetRepository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Please upload PDF first. chatId=" + chatId));

        // 保存会话ID到历史（防止用户直接调用chat未走upload）
        chatHistoryRepository.save("pdf", chatId);

        // 从全文中抽取“相关片段”给模型（避免塞全文）
        String context = buildRelevantContext(asset.getPdfText(), prompt);

        String system = """
                你是一个严谨的 PDF 文档问答助手。
                你必须只依据【PDF片段】回答问题；如果片段中没有答案，请明确说“文档中没有相关信息”，不要编造。
                
                【PDF文件名】
                %s
                
                【PDF片段】
                %s
                """.formatted(asset.getFileName(), context);

        return chatClient.prompt()
                .system(system)
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    // ----------------- helpers -----------------

    //
    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            // 简单清洗
            return text == null ? "" : text.replace("\u0000", "").trim();
        }
    }

    /**
     * 极简“检索”：从全文里截取包含关键词的若干窗口片段
     * - 不引入向量库即可工作
     * - 后续你想升级为 VectorStore/RAG，这里直接替换即可
     */
    private String buildRelevantContext(String fullText, String prompt) {
        if (fullText == null || fullText.isBlank()) return "（文档文本为空或解析失败）";
        if (prompt == null) prompt = "";

        // 关键词：按中文/英文/数字切分，取较长的词，去重
        Set<String> keywords = Arrays.stream(prompt
                        .replaceAll("[\\p{Punct}，。！？、；：“”‘’（）()\\[\\]{}<>]", " ")
                        .split("\\s+"))
                .filter(s -> s.length() >= 2)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 没关键词：给开头一段
        if (keywords.isEmpty()) {
            return clip(fullText, 0, 2500);
        }

        String lower = fullText.toLowerCase();
        List<String> snippets = new ArrayList<>();

        int window = 600;      // 命中点前后窗口
        int maxSnippets = 6;   // 最多取几段
        int maxTotal = 2800;   // 最终给模型的最大字符（可调）

        for (String kw : keywords) {
            int idx = lower.indexOf(kw);
            if (idx < 0) continue;

            int start = Math.max(0, idx - window);
            int end = Math.min(fullText.length(), idx + window);
            snippets.add(fullText.substring(start, end).trim());

            if (snippets.size() >= maxSnippets) break;
        }

        if (snippets.isEmpty()) {
            // 找不到关键词：给摘要策略（这里用开头+中间+结尾拼一下）
            String a = clip(fullText, 0, 900);
            String b = clip(fullText, Math.max(0, fullText.length() / 2 - 450), 900);
            String c = clip(fullText, Math.max(0, fullText.length() - 900), 900);
            String merged = a + "\n...\n" + b + "\n...\n" + c;
            return clip(merged, 0, maxTotal);
        }

        String merged = String.join("\n\n---\n\n", snippets);
        return clip(merged, 0, maxTotal);
    }

    private String clip(String s, int start, int maxLen) {
        if (s == null) return "";
        if (start < 0) start = 0;
        if (start >= s.length()) return "";
        int end = Math.min(s.length(), start + maxLen);
        return s.substring(start, end);
    }
}
