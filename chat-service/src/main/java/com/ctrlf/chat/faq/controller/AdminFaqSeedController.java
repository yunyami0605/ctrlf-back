package com.ctrlf.chat.faq.controller;

import com.ctrlf.chat.faq.service.FaqSeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * FAQ Seed CSV 업로드 관리자 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/faq/seed")
public class AdminFaqSeedController {

    private final FaqSeedService faqSeedService;

    /**
     * FAQ 초기 데이터 CSV 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<Void> uploadSeedCsv(
        @RequestPart MultipartFile file,
        @RequestParam UUID operatorId
    ) {
        faqSeedService.uploadSeedCsv(file, operatorId);
        return ResponseEntity.ok().build();
    }
}
