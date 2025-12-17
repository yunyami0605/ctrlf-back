package com.ctrlf.chat.faq.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * FAQ 초기 데이터(Seed) 관리 서비스
 */
public interface FaqSeedService {

    /**
     * FAQ Seed CSV 파일 업로드
     */
    void uploadSeedCsv(MultipartFile file, UUID operatorId);
}
