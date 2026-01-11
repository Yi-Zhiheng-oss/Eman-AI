package com.itheima.ai.pdf;

import java.util.Optional;

public interface PdfAssetRepository {

    void save(PdfAsset asset);

    Optional<PdfAsset> findByChatId(String chatId);

    boolean exists(String chatId);
}
