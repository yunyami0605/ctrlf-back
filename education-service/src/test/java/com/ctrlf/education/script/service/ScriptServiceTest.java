package com.ctrlf.education.script.service;

import com.ctrlf.education.script.dto.EducationScriptDto.ScriptDetailResponse;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptLookupResponse;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptResponse;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptUpdateRequest;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptUpdateResponse;
import com.ctrlf.education.script.entity.EducationScript;
import com.ctrlf.education.script.entity.EducationScriptChapter;
import com.ctrlf.education.script.entity.EducationScriptScene;
import com.ctrlf.education.script.repository.EducationScriptChapterRepository;
import com.ctrlf.education.script.repository.EducationScriptRepository;
import com.ctrlf.education.script.repository.EducationScriptSceneRepository;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.SourceSet;
import com.ctrlf.education.video.repository.EducationVideoRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScriptService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptService 테스트")
class ScriptServiceTest {

    @Mock
    private EducationScriptRepository scriptRepository;

    @Mock
    private EducationScriptChapterRepository chapterRepository;

    @Mock
    private EducationScriptSceneRepository sceneRepository;

    @Mock
    private EducationVideoRepository videoRepository;

    @Mock
    private SourceSetRepository sourceSetRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ScriptService scriptService;

    private UUID testScriptId;
    private UUID testEducationId;
    private UUID testVideoId;
    private UUID testChapterId;
    private UUID testSceneId;
    private UUID testSourceSetId;
    private EducationScript testScript;
    private EducationScriptChapter testChapter;
    private EducationScriptScene testScene;
    private EducationVideo testVideo;
    private SourceSet testSourceSet;

    @BeforeEach
    void setUp() {
        testScriptId = UUID.randomUUID();
        testEducationId = UUID.randomUUID();
        testVideoId = UUID.randomUUID();
        testChapterId = UUID.randomUUID();
        testSceneId = UUID.randomUUID();
        testSourceSetId = UUID.randomUUID();

        testScript = new EducationScript();
        testScript.setId(testScriptId);
        testScript.setEducationId(testEducationId);
        testScript.setTitle("테스트 스크립트");
        testScript.setVersion(1);
        testScript.setTotalDurationSec(600);
        testScript.setLlmModel("gpt-4");
        testScript.setRawPayload("{\"test\": \"data\"}");
        testScript.setStatus("DRAFT");
        testScript.setSourceSetId(testSourceSetId);

        testChapter = new EducationScriptChapter();
        testChapter.setId(testChapterId);
        testChapter.setScriptId(testScriptId);
        testChapter.setChapterIndex(0);
        testChapter.setTitle("테스트 챕터");
        testChapter.setDurationSec(300);

        testScene = new EducationScriptScene();
        testScene.setId(testSceneId);
        testScene.setScriptId(testScriptId);
        testScene.setChapterId(testChapterId);
        testScene.setSceneIndex(0);
        testScene.setPurpose("소개");
        testScene.setNarration("테스트 나레이션");
        testScene.setCaption("테스트 캡션");
        testScene.setVisual("테스트 비주얼");
        testScene.setDurationSec(60);
        testScene.setConfidenceScore(0.95f);

        testVideo = EducationVideo.create(
            testEducationId,
            "테스트 영상",
            null,
            600,
            1,
            com.ctrlf.education.video.dto.VideoDtos.VideoStatus.PUBLISHED
        );
        testVideo.setId(testVideoId);
        testVideo.setScriptId(testScriptId);

        testSourceSet = com.ctrlf.education.video.entity.SourceSet.create(
            "테스트 소스셋",
            "test",
            UUID.randomUUID().toString(),
            testEducationId,
            testVideoId
        );
        ReflectionTestUtils.setField(testSourceSet, "id", testSourceSetId);
    }

    @Test
    @DisplayName("스크립트 상세 조회 - 성공")
    void getScript_Success() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(sourceSetRepository.findById(testSourceSetId)).thenReturn(Optional.of(testSourceSet));
        when(chapterRepository.findByScriptIdOrderByChapterIndexAsc(testScriptId))
            .thenReturn(List.of(testChapter));
        when(sceneRepository.findByChapterIdOrderBySceneIndexAsc(testChapterId))
            .thenReturn(List.of(testScene));

        // when
        ScriptDetailResponse result = scriptService.getScript(testScriptId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.scriptId()).isEqualTo(testScriptId);
        assertThat(result.educationId()).isEqualTo(testEducationId);
        assertThat(result.videoId()).isEqualTo(testVideoId);
        assertThat(result.title()).isEqualTo("테스트 스크립트");
        assertThat(result.chapters()).hasSize(1);
        assertThat(result.chapters().get(0).scenes()).hasSize(1);
    }

    @Test
    @DisplayName("스크립트 상세 조회 - 스크립트를 찾을 수 없음")
    void getScript_NotFound() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scriptService.getScript(testScriptId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("스크립트 목록 조회 - 성공")
    void listScripts_Success() {
        // given
        Page<EducationScript> page = new PageImpl<>(List.of(testScript));
        when(scriptRepository.findAll(PageRequest.of(0, 10))).thenReturn(page);

        // when
        List<ScriptResponse> result = scriptService.listScripts(0, 10);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).scriptId()).isEqualTo(testScriptId);
        assertThat(result.get(0).educationId()).isEqualTo(testEducationId);
    }

    @Test
    @DisplayName("스크립트 ID 조회 - videoId로 조회 성공")
    void findScriptId_ByVideoId_Success() {
        // given
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));

        // when
        ScriptLookupResponse result = scriptService.findScriptId(testVideoId, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.scriptId()).isEqualTo(testScriptId);
        assertThat(result.videoId()).isEqualTo(testVideoId);
        assertThat(result.educationId()).isEqualTo(testEducationId);
    }

    @Test
    @DisplayName("스크립트 ID 조회 - videoId로 조회 시 영상을 찾을 수 없음")
    void findScriptId_ByVideoId_VideoNotFound() {
        // given
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scriptService.findScriptId(testVideoId, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("스크립트 ID 조회 - educationId로 조회 성공")
    void findScriptId_ByEducationId_Success() {
        // given
        when(scriptRepository.findByEducationIdAndDeletedAtIsNullOrderByVersionDesc(testEducationId))
            .thenReturn(List.of(testScript));
        when(videoRepository.findByScriptId(testScriptId))
            .thenReturn(List.of(testVideo));

        // when
        ScriptLookupResponse result = scriptService.findScriptId(null, testEducationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.scriptId()).isEqualTo(testScriptId);
        assertThat(result.educationId()).isEqualTo(testEducationId);
        assertThat(result.videoId()).isEqualTo(testVideoId);
    }

    @Test
    @DisplayName("스크립트 ID 조회 - educationId로 조회 시 스크립트 없음")
    void findScriptId_ByEducationId_NoScript() {
        // given
        when(scriptRepository.findByEducationIdAndDeletedAtIsNullOrderByVersionDesc(testEducationId))
            .thenReturn(Collections.emptyList());

        // when & then
        assertThatThrownBy(() -> scriptService.findScriptId(null, testEducationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("스크립트 ID 조회 - videoId와 educationId 모두 없음")
    void findScriptId_BothNull() {
        // when & then
        assertThatThrownBy(() -> scriptService.findScriptId(null, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(400);
    }

    @Test
    @DisplayName("렌더 스펙 조회 - 성공")
    void getRenderSpec_Success() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(videoRepository.findByScriptId(testScriptId)).thenReturn(List.of(testVideo));
        when(chapterRepository.findByScriptIdOrderByChapterIndexAsc(testScriptId))
            .thenReturn(List.of(testChapter));
        when(sceneRepository.findByChapterIdOrderBySceneIndexAsc(testChapterId))
            .thenReturn(List.of(testScene));

        // when
        var result = scriptService.getRenderSpec(testScriptId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.scriptId()).isEqualTo(testScriptId.toString());
        assertThat(result.videoId()).isEqualTo(testVideoId.toString());
        assertThat(result.title()).isEqualTo("테스트 스크립트");
        assertThat(result.scenes()).hasSize(1);
    }

    @Test
    @DisplayName("렌더 스펙 조회 - 스크립트를 찾을 수 없음")
    void getRenderSpec_NotFound() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scriptService.getRenderSpec(testScriptId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("스크립트 삭제 - 성공")
    void deleteScript_Success() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(sceneRepository.findByScriptIdOrderByChapterIdAscSceneIndexAsc(testScriptId))
            .thenReturn(List.of(testScene));
        when(chapterRepository.findByScriptIdOrderByChapterIndexAsc(testScriptId))
            .thenReturn(List.of(testChapter));

        // when
        scriptService.deleteScript(testScriptId);

        // then
        verify(sceneRepository).deleteAll(List.of(testScene));
        verify(chapterRepository).deleteAll(List.of(testChapter));
        verify(scriptRepository).delete(testScript);
    }

    @Test
    @DisplayName("스크립트 삭제 - 스크립트를 찾을 수 없음")
    void deleteScript_NotFound() {
        // given
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scriptService.deleteScript(testScriptId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("스크립트 수정 - 성공")
    void updateScript_Success() {
        // given
        ScriptUpdateRequest request = new ScriptUpdateRequest(
            "{\"updated\": \"script\"}",
            null
        );
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(scriptRepository.save(any(EducationScript.class))).thenAnswer(invocation -> {
            EducationScript saved = invocation.getArgument(0);
            return saved;
        });

        // when
        ScriptUpdateResponse result = scriptService.updateScript(testScriptId, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.updated()).isTrue();
        assertThat(result.scriptId()).isEqualTo(testScriptId);
        verify(scriptRepository).save(any(EducationScript.class));
    }

    @Test
    @DisplayName("스크립트 수정 - 스크립트를 찾을 수 없음")
    void updateScript_NotFound() {
        // given
        ScriptUpdateRequest request = new ScriptUpdateRequest(null, null);
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> scriptService.updateScript(testScriptId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }
}
