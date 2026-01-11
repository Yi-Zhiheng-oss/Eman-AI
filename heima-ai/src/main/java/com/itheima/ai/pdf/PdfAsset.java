package com.itheima.ai.pdf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfAsset {
    private String chatId;
    private String fileName;
    private String contentType; // application/pdf
    private byte[] pdfBytes;    // 原始PDF
    private String pdfText;     // 抽取出的文本
    private long uploadTime;
}
