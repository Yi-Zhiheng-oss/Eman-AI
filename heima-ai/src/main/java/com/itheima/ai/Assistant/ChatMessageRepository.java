package com.itheima.ai.Assistant;

import java.util.List;

public interface ChatMessageRepository {

    void append(String type, String chatId, ChatMessage message);

    List<ChatMessage> list(String type, String chatId);
}
