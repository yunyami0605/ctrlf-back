package com.ctrlf.infra.config;

import com.ctrlf.infra.rag.entity.RagDocument;
import com.ctrlf.infra.rag.entity.RagDocumentChunk;
import com.ctrlf.infra.rag.repository.RagDocumentChunkRepository;
import com.ctrlf.infra.rag.repository.RagDocumentRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG 도메인 로컬 시드.
 * 활성화: --spring.profiles.active=local,local-seed
 */
@Profile("local-seed")
@Order(1)
@Component
public class RagSeedRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(RagSeedRunner.class);

    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;

    public RagSeedRunner(
        RagDocumentRepository documentRepository,
        RagDocumentChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedDocuments();
    }

    private void seedDocuments() {
        // 샘플 1
        RagDocument d1 = new RagDocument();
        d1.setTitle("산업안전 규정집 v3");
        d1.setDomain("HR");
        d1.setUploaderUuid("c13c91f2-fb1a-4d42-b381-72847a52fb99");
        d1.setSourceUrl("s3://ctrl-s3/docs/hr_safety_v3.pdf");
        d1.setStatus("QUEUED");
        d1.setCreatedAt(Instant.now());
        d1 = documentRepository.save(d1);
        log.info("Seed created: RagDocument id={}, title={}", d1.getId(), d1.getTitle());

        createSampleChunks(d1.getId(), 2);

        // 샘플 2
        RagDocument d2 = new RagDocument();
        d2.setTitle("개발 보안 가이드 v1");
        d2.setDomain("SEC");
        d2.setUploaderUuid("00000000-0000-0000-0000-000000000000");
        d2.setSourceUrl("s3://ctrl-s3/docs/devsec_v1.pdf");
        d2.setStatus("QUEUED");
        d2.setCreatedAt(Instant.now());
        d2 = documentRepository.save(d2);
        log.info("Seed created: RagDocument id={}, title={}", d2.getId(), d2.getTitle());

        createSampleChunks(d2.getId(), 1);
    }

    private void createSampleChunks(UUID documentId, int count) {
        for (int i = 0; i < count; i++) {
            RagDocumentChunk c = new RagDocumentChunk();
            c.setDocumentId(documentId);
            c.setChunkIndex(i);
            c.setChunkText("샘플 청크 내용 " + (i + 1));
            // embedding은 로컬에서는 null 로 두어도 됩니다.
            c.setCreatedAt(Instant.now());
            chunkRepository.save(c);
        }
        log.info("Seed created: {} chunks for documentId={}", count, documentId);
    }
}

