package com.itheima.ai.Assistant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    // "user" / "assistant"
    private String role;
    private String content;
}
