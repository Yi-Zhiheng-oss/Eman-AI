package com.itheima.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.openai.OpenAiChatModel;
@Configuration
public class CommonConfiguration {


    // 创建一个InMemoryChatMemory对象，用于存储聊天记录
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }


     //创建一个ChatClient对象，用于处理聊天请求
    @Bean
    public ChatClient chatClient(OllamaChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem("你是一个热心、可爱的智能助手，你的名字叫小小Eman，请以小小Eman的身份和语气回答问题。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

//    @Bean
//    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory) {
//        return ChatClient
//                .builder(model)
//                .defaultSystem("你是一个热心、可爱的智能助手，你的名字叫小小Eman，请以小小Eman的身份和语气回答问题。")
//                .defaultAdvisors(
//                        new SimpleLoggerAdvisor(),
//                        new MessageChatMemoryAdvisor(chatMemory)
//                )
//                .build();
//    }
}
