package com.itheima.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryServiceKbRepository implements ServiceKbRepository {


    //实现加载知识库资料的功能

    private final ObjectMapper objectMapper;

    private final List<ServiceKbItem> items = new ArrayList<>();
    private static final String FILE = "service-kb.json";

    @Override
    public List<ServiceKbItem> listAll() {
        return List.copyOf(items);
    }

    @PostConstruct
    private void init() {
        FileSystemResource res = new FileSystemResource(FILE);
        try {
            if (res.exists()) {
                List<ServiceKbItem> data = objectMapper.readValue(res.getInputStream(), new TypeReference<>() {});
                items.clear();
                items.addAll(data);
                log.info("Loaded service KB: {} items.", items.size());
                return;
            }
        } catch (Exception e) {
            log.warn("Failed to load {}, fallback to defaults.", FILE, e);
        }

        // 默认内置几条（你可以自行扩展/改成从 DB 读）
        items.add(new ServiceKbItem("kb-001", "课程咨询：Java", """
                Java就业班包含：JavaSE、Spring、SpringBoot、MyBatis、微服务、项目实战、面试辅导等。
                适合零基础/转行/提升。可提供试听与学习计划建议。
                """, new String[]{"java","课程","就业","学习路线"}));

        items.add(new ServiceKbItem("kb-002", "预约试听", """
                你可以告诉我：意向课程、城市/线上、方便的时间段、联系方式（可选），我会为你生成预约编号。
                """, new String[]{"预约","试听","报名","咨询"}));

        items.add(new ServiceKbItem("kb-003", "售后/退款", """
                售后问题请提供：订单号/手机号/购买渠道，我们将协助处理。
                """, new String[]{"售后","退款","订单"}));

        log.info("Initialized service KB with default {} items. You can create {} to override.", items.size(), FILE);
    }
}
