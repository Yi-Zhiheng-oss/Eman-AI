package com.itheima.ai.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceKbItem {
    private String id;
    private String title;
    private String content;  // 具体答案/说明
    private String[] tags;   // 关键词标签（便于简单检索）
}
