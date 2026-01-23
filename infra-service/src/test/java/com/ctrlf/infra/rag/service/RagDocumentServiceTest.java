package com.ctrlf.infra.rag.service;

import static com.ctrlf.infra.rag.dto.RagDtos.*;
import com.ctrlf.infra.config.metrics.CustomMetrics;
import com.ctrlf.infra.rag.entity.RagDocument;
import com.ctrlf.infra.rag.entity.RagDocumentStatus;
import com.ctrlf.infra.rag.repository.RagDocumentChunkRepository;
import com.ctrlf.infra.rag.repository.RagDocumentHistoryRepository;
import com.ctrlf.infra.rag.repository.RagDocumentRepository;
import com.ctrlf.infra.rag.repository.RagFailChunkRepository;
import com.ctrlf.infra.rag.client.RagAiClient;
import com.ctrlf.infra.s3.service.S3Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagDocumentService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagDocumentService 테스트")
class RagDocumentServiceTest {

    @Mock
    private RagDocumentRepository documentRepository;

    @Mock
    private RagDocumentChunkRepository chunkRepository;

    @Mock
    private RagFailChunkRepository failChunkRepository;

    @Mock
    private RagDocumentHistoryRepository historyRepository;

    @Mock
    private RagAiClient ragAiClient;

    @Mock
    private S3Service s3Service;

    @Mock
    private CustomMetrics customMetrics;

    @InjectMocks
    private RagDocumentService ragDocumentService;

    private UUID testDocumentId;
    private UUID testUploaderId;
    private RagDocument testDocument;
    private String testDocumentIdStr;

    @BeforeEach
    void setUp() {
        testDocumentId = UUID.randomUUID();
        testUploaderId = UUID.randomUUID();
        testDocumentIdStr = testDocumentId.toString();

        testDocument = new RagDocument();
        testDocument.setId(testDocumentId);
        testDocument.setTitle("테스트 문서");
        testDocument.setDomain("test");
        testDocument.setUploaderUuid(testUploaderId.toString());
        testDocument.setSourceUrl("s3://bucket/file.pdf");
        testDocument.setVersion(1);
        testDocument.setStatus(RagDocumentStatus.QUEUED);
        testDocument.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("문서 업로드 - 성공")
    void upload_Success() {
        // given
        UploadRequest request = new UploadRequest();
        ReflectionTestUtils.setField(request, "title", "새 문서");
        ReflectionTestUtils.setField(request, "domain", "test");
        ReflectionTestUtils.setField(request, "fileUrl", "s3://bucket/file.pdf");

        when(documentRepository.save(any(RagDocument.class))).thenAnswer(invocation -> {
            RagDocument saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", testDocumentId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        // when
        UploadResponse result = ragDocumentService.upload(request, testUploaderId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(testDocumentIdStr);
        assertThat(result.getStatus()).isEqualTo(RagDocumentStatus.QUEUED.name());
        verify(documentRepository).save(any(RagDocument.class));
    }

    @Test
    @DisplayName("문서 업데이트 - 성공")
    void update_Success() {
        // given
        UpdateRequest request = new UpdateRequest();
        ReflectionTestUtils.setField(request, "title", "수정된 제목");
        ReflectionTestUtils.setField(request, "domain", "updated");
        ReflectionTestUtils.setField(request, "fileUrl", "s3://bucket/new-file.pdf");

        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(RagDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        try {
            when(s3Service.presignDownload(anyString(), any())).thenReturn(
                java.net.URI.create("https://presigned-url.com/file.pdf").toURL()
            );
            when(ragAiClient.ingest(any(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RagAiClient.AiResponse(true, testDocumentIdStr, "doc-id", 1, "PROCESSING", "request-id", "trace-id"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // when
        UpdateResponse result = ragDocumentService.update(testDocumentIdStr, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(testDocumentIdStr);
        assertThat(result.getStatus()).isEqualTo("REPROCESSING");
        verify(documentRepository).save(any(RagDocument.class));
    }

    @Test
    @DisplayName("문서 업데이트 - 문서를 찾을 수 없음")
    void update_DocumentNotFound() {
        // given
        UpdateRequest request = new UpdateRequest();
        ReflectionTestUtils.setField(request, "title", "수정된 제목");
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ragDocumentService.update(testDocumentIdStr, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("문서 업데이트 - 변경할 필드 없음")
    void update_NoFieldsToUpdate() {
        // given
        UpdateRequest request = new UpdateRequest();
        // 모든 필드가 null
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocument));

        // when & then
        assertThatThrownBy(() -> ragDocumentService.update(testDocumentIdStr, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(400);
    }

    @Test
    @DisplayName("문서 삭제 - 성공")
    void delete_Success() {
        // given
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocument));
        // deleteByDocumentId는 void 메서드이므로 스텁이 필요 없음

        // when
        DeleteResponse result = ragDocumentService.delete(testDocumentIdStr);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(testDocumentIdStr);
        assertThat(result.getStatus()).isEqualTo("DELETED");
        verify(documentRepository).delete(any(RagDocument.class));
    }

    @Test
    @DisplayName("문서 삭제 - 문서를 찾을 수 없음")
    void delete_DocumentNotFound() {
        // given
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ragDocumentService.delete(testDocumentIdStr))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("문서 조회 - 성공")
    void getDocument_Success() {
        // given
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocument));

        // when
        DocumentInfoResponse result = ragDocumentService.getDocument(testDocumentIdStr);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testDocumentIdStr);
        assertThat(result.getTitle()).isEqualTo("테스트 문서");
        assertThat(result.getStatus()).isEqualTo(RagDocumentStatus.QUEUED.name());
    }

    @Test
    @DisplayName("문서 조회 - 문서를 찾을 수 없음")
    void getDocument_NotFound() {
        // given
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ragDocumentService.getDocument(testDocumentIdStr))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("문서 상태 조회 - 성공")
    void getStatus_Success() {
        // given
        when(documentRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocument));

        // when
        DocumentStatusResponse result = ragDocumentService.getStatus(testDocumentIdStr);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(testDocumentIdStr);
        assertThat(result.getStatus()).isEqualTo(RagDocumentStatus.QUEUED.name());
    }

    @Test
    @DisplayName("문서 목록 조회 - 성공")
    void list_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<RagDocument> page = new PageImpl<>(List.of(testDocument), pageable, 1);
        when(documentRepository.findAllByDomainContainingIgnoreCaseAndUploaderUuidContainingIgnoreCaseAndTitleContainingIgnoreCaseAndCreatedAtBetween(
            anyString(), anyString(), anyString(), any(), any(), any(Pageable.class)
        )).thenReturn(page);

        // when
        List<DocumentListItem> result = ragDocumentService.list(
            null, null, null, null, null, 0, 10
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(testDocumentIdStr);
    }

    @Test
    @DisplayName("문서 목록 조회 - 잘못된 날짜 형식")
    void list_InvalidDateFormat() {
        // when & then
        assertThatThrownBy(() -> ragDocumentService.list(
            null, null, "invalid-date", null, null, 0, 10
        )).isInstanceOf(ResponseStatusException.class)
          .extracting("statusCode.value")
          .isEqualTo(400);
    }
}
