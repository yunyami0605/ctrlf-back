package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.request.FaqSeedRow;
import com.ctrlf.chat.faq.entity.Faq;
import com.ctrlf.chat.faq.entity.FaqRevision;
import com.ctrlf.chat.faq.repository.FaqRepository;
import com.ctrlf.chat.faq.repository.FaqRevisionRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class FaqSeedServiceImpl implements FaqSeedService {

    private final FaqRepository faqRepository;
    private final FaqRevisionRepository faqRevisionRepository;

    @Override
    public void uploadSeedCsv(MultipartFile file, UUID operatorId) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            )
        ) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] cols = line.split(",", -1);
                if (cols.length < 3) continue;

                FaqSeedRow row = new FaqSeedRow();
                row.setDomain(cols[0].trim());
                row.setQuestion(cols[1].trim());
                row.setAnswerMarkdown(cols[2].trim());
                row.setSummary(cols.length >= 4 ? cols[3].trim() : null);

                upsertFaq(row, operatorId);
            }

        } catch (Exception e) {
            throw new IllegalStateException("Seed FAQ CSV 업로드 실패", e);
        }
    }

    private void upsertFaq(FaqSeedRow row, UUID operatorId) {
        Optional<Faq> existing =
            faqRepository.findByQuestionAndDomain(row.getQuestion(), row.getDomain());

        Faq faq;
        String action;

        if (existing.isPresent()) {
            faq = existing.get();
            faq.setAnswer(row.getAnswerMarkdown());
            faq.setUpdatedAt(Instant.now());
            action = "SEED_UPDATE";
        } else {
            faq = new Faq();
            faq.setDomain(row.getDomain());
            faq.setQuestion(row.getQuestion());
            faq.setAnswer(row.getAnswerMarkdown());
            faq.setIsActive(true);
            faq.setPublishedAt(Instant.now());
            faq.setCreatedAt(Instant.now());
            faq.setUpdatedAt(Instant.now());
            action = "SEED_CREATE";
        }

        faqRepository.save(faq);

        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ",
                faq.getId(),
                action,
                operatorId,
                "Seed CSV Upload"
            )
        );
    }
}
