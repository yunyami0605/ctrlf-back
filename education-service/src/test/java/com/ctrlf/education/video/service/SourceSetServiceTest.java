package com.ctrlf.education.video.service;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.script.service.ScriptService;
import com.ctrlf.education.video.client.SourceSetAiClient;
import com.ctrlf.education.video.dto.VideoDtos.S3DownloadResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCreateRequest;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCreateResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetUpdateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.SourceSet;
import com.ctrlf.education.video.entity.SourceSetDocument;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import com.ctrlf.education.video.repository.SourceSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SourceSetService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SourceSetService 테스트")
class SourceSetServiceTest {

    @Mock
    private SourceSetRepository sourceSetRepository;

    @Mock
    private SourceSetDocumentRepository sourceSetDocumentRepository;

    @Mock
    private SourceSetAiClient sourceSetAiClient;

    @Mock
    private EducationVideoRepository videoRepository;

    @Mock
    private EducationRepository educationRepository;

    @Mock
    private InfraRagClient infraRagClient;

    @Mock
    private ScriptService scriptService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SourceSetService sourceSetService;

    private UUID testSourceSetId;
    private UUID testEducationId;
    private UUID testVideoId;
    private UUID testUserId;
    private UUID testDocumentId;
    private SourceSet testSourceSet;
    private EducationVideo testVideo;
    private Education testEducation;
    private SourceSetDocument testDocument;

    @BeforeEach
    void setUp() {
        testSourceSetId = UUID.randomUUID();
        testEducationId = UUID.randomUUID();
        testVideoId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testDocumentId = UUID.randomUUID();

        testEducation = new Education();
        testEducation.setId(testEducationId);
        testEducation.setTitle("테스트 교육");
        testEducation.setCategory(EducationTopic.JOB_DUTY);
        testEducation.setEduType(EducationCategory.MANDATORY);
        testEducation.setDepartmentScope(new String[]{"개발팀"});

        testVideo = EducationVideo.create(
            testEducationId,
            "테스트 영상",
            null,
            600,
            1,
            VideoStatus.DRAFT
        );
        testVideo.setId(testVideoId);

        testSourceSet = SourceSet.create(
            "테스트 소스셋",
            "test",
            testUserId.toString(),
            testEducationId,
            testVideoId
        );
        ReflectionTestUtils.setField(testSourceSet, "id", testSourceSetId);

        testDocument = SourceSetDocument.create(testSourceSet, testDocumentId);
    }

    @Test
    @DisplayName("소스셋 생성 - 성공")
    void createSourceSet_Success() {
        // given
        SourceSetCreateRequest request = new SourceSetCreateRequest(
            "새 소스셋",
            "test",
            List.of(testDocumentId.toString()),
            testEducationId,
            testVideoId
        );

        when(videoRepository.existsById(testVideoId)).thenReturn(true);
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));
        when(sourceSetRepository.save(any(SourceSet.class))).thenAnswer(invocation -> {
            SourceSet saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", testSourceSetId);
            return saved;
        });
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSetDocumentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(sourceSetService, "autoSourceSetCallback", false);
        when(sourceSetAiClient.startSourceSet(anyString(), any())).thenReturn(
            new com.ctrlf.education.video.client.SourceSetAiDtos.StartResponse(true, testSourceSetId.toString(), "PROCESSING")
        );

        // when
        SourceSetCreateResponse result = sourceSetService.createSourceSet(request, testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.sourceSetId()).isEqualTo(testSourceSetId.toString());
        assertThat(result.documentIds()).hasSize(1);
        verify(sourceSetRepository).save(any(SourceSet.class));
        verify(videoRepository).save(any(EducationVideo.class));
    }

    @Test
    @DisplayName("소스셋 생성 - 영상을 찾을 수 없음")
    void createSourceSet_VideoNotFound() {
        // given
        SourceSetCreateRequest request = new SourceSetCreateRequest(
            "새 소스셋",
            "test",
            List.of(testDocumentId.toString()),
            testEducationId,
            testVideoId
        );
        when(videoRepository.existsById(testVideoId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> sourceSetService.createSourceSet(request, testUserId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("소스셋 생성 - 교육을 찾을 수 없음")
    void createSourceSet_EducationNotFound() {
        // given
        SourceSetCreateRequest request = new SourceSetCreateRequest(
            "새 소스셋",
            "test",
            List.of(testDocumentId.toString()),
            testEducationId,
            testVideoId
        );
        when(videoRepository.existsById(testVideoId)).thenReturn(true);
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sourceSetService.createSourceSet(request, testUserId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("소스셋 문서 변경 - 성공")
    void updateSourceSetDocuments_Success() {
        // given
        SourceSetUpdateRequest request = new SourceSetUpdateRequest(
            List.of(testDocumentId.toString()),
            Collections.emptyList(),
            "문서 추가"
        );
        when(sourceSetRepository.findByIdAndNotDeleted(testSourceSetId)).thenReturn(Optional.of(testSourceSet));
        when(sourceSetDocumentRepository.findBySourceSetId(testSourceSetId)).thenReturn(Collections.emptyList());
        when(sourceSetDocumentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSetRepository.save(any(SourceSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceSetDocumentRepository.findBySourceSetId(testSourceSetId))
            .thenReturn(List.of(testDocument));

        // when
        SourceSetCreateResponse result = sourceSetService.updateSourceSetDocuments(testSourceSetId, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.sourceSetId()).isEqualTo(testSourceSetId.toString());
        verify(sourceSetDocumentRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("소스셋 문서 변경 - LOCKED 상태는 변경 불가")
    void updateSourceSetDocuments_Locked() {
        // given
        testSourceSet.setStatus("LOCKED");
        SourceSetUpdateRequest request = new SourceSetUpdateRequest(
            Collections.emptyList(),
            Collections.emptyList(),
            null
        );
        when(sourceSetRepository.findByIdAndNotDeleted(testSourceSetId)).thenReturn(Optional.of(testSourceSet));

        // when & then
        assertThatThrownBy(() -> sourceSetService.updateSourceSetDocuments(testSourceSetId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(409);
    }

    @Test
    @DisplayName("소스셋 조회 - 성공")
    void getSourceSet_Success() {
        // given
        when(sourceSetRepository.findByIdAndNotDeleted(testSourceSetId)).thenReturn(Optional.of(testSourceSet));

        // when
        SourceSet result = sourceSetService.getSourceSet(testSourceSetId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testSourceSetId);
    }

    @Test
    @DisplayName("소스셋 조회 - 소스셋을 찾을 수 없음")
    void getSourceSet_NotFound() {
        // given
        when(sourceSetRepository.findByIdAndNotDeleted(testSourceSetId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sourceSetService.getSourceSet(testSourceSetId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("문서 ID 목록 조회 - 성공")
    void getDocumentIds_Success() {
        // given
        when(sourceSetDocumentRepository.findBySourceSetId(testSourceSetId))
            .thenReturn(List.of(testDocument));

        // when
        List<UUID> result = sourceSetService.getDocumentIds(testSourceSetId);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testDocumentId);
    }

    @Test
    @DisplayName("S3 Presigned URL 조회 - 성공")
    void getPresignedDownloadUrl_Success() {
        // given
        String fileUrl = "s3://bucket/file.pdf";
        String presignedUrl = "https://presigned-url.com/file.pdf";
        when(infraRagClient.getPresignedDownloadUrl(fileUrl)).thenReturn(presignedUrl);

        // when
        S3DownloadResponse result = sourceSetService.getPresignedDownloadUrl(fileUrl);

        // then
        assertThat(result).isNotNull();
        assertThat(result.downloadUrl()).isEqualTo(presignedUrl);
    }
}
