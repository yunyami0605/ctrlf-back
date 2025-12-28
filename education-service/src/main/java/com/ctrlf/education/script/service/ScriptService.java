package com.ctrlf.education.script.service;

import com.ctrlf.education.script.dto.EducationScriptDto.ChapterItem;
import com.ctrlf.education.script.dto.EducationScriptDto.ChapterUpsert;
import com.ctrlf.education.script.dto.EducationScriptDto.SceneItem;
import com.ctrlf.education.script.dto.EducationScriptDto.SceneUpsert;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptCompleteCallback;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptCompleteResponse;
import com.ctrlf.education.script.dto.EducationScriptDto.ScriptDetailResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ScriptService {
  private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

  private final EducationScriptRepository scriptRepository;
  private final EducationScriptChapterRepository chapterRepository;
  private final EducationScriptSceneRepository sceneRepository;
  private final EducationVideoRepository videoRepository;
  private final ObjectMapper objectMapper;
  private final com.ctrlf.education.video.repository.SourceSetRepository sourceSetRepository;

  @Transactional(readOnly = true)
  public ScriptDetailResponse getScript(UUID scriptId) {
    EducationScript script =
        scriptRepository
            .findById(scriptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + scriptId));

    // sourceSetId를 통해 videoId 조회
    UUID videoId = null;
    if (script.getSourceSetId() != null) {
      videoId = sourceSetRepository
          .findById(script.getSourceSetId())
          .map(com.ctrlf.education.video.entity.SourceSet::getVideoId)
          .orElse(null);
    }

    List<EducationScriptChapter> chapters =
        chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId);
    List<ChapterItem> chapterItems = new ArrayList<>();
    for (EducationScriptChapter ch : chapters) {
      List<EducationScriptScene> scenes =
          sceneRepository.findByChapterIdOrderBySceneIndexAsc(ch.getId());
      List<SceneItem> sceneItems = new ArrayList<>();
      for (EducationScriptScene sc : scenes) {
        sceneItems.add(
            new SceneItem(
                sc.getId(),
                sc.getSceneIndex(),
                sc.getPurpose(),
                sc.getNarration(),
                sc.getCaption(),
                sc.getVisual(),
                sc.getDurationSec(),
                sc.getSourceChunkIndexes(),
                sc.getConfidenceScore()));
      }
      chapterItems.add(
          new ChapterItem(
              ch.getId(), ch.getChapterIndex(), ch.getTitle(), ch.getDurationSec(), sceneItems));
    }

    return new ScriptDetailResponse(
        script.getId(),
        script.getEducationId(),
        videoId,
        script.getTitle(),
        script.getTotalDurationSec(),
        script.getVersion(),
        script.getLlmModel(),
        script.getRawPayload(),
        chapterItems);
  }

  @Transactional(readOnly = true)
  public List<ScriptResponse> listScripts(int page, int size) {
    Page<EducationScript> pageRes = scriptRepository.findAll(PageRequest.of(page, size));
    List<ScriptResponse> list = new ArrayList<>();
    for (EducationScript s : pageRes.getContent()) {
      list.add(
          new ScriptResponse(
              s.getId(), s.getEducationId(), s.getRawPayload(), s.getVersion()));
    }
    return list;
  }

  /**
   * videoId 또는 educationId로 스크립트 ID 조회.
   * 둘 다 제공된 경우 videoId 우선.
   *
   * @param videoId 영상 ID (선택)
   * @param educationId 교육 ID (선택)
   * @return 스크립트 조회 응답
   */
  @Transactional(readOnly = true)
  public com.ctrlf.education.script.dto.EducationScriptDto.ScriptLookupResponse findScriptId(
      UUID videoId, UUID educationId) {
    
    // 1. videoId로 조회 (우선)
    if (videoId != null) {
      EducationVideo video = videoRepository.findById(videoId)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다: " + videoId));
      
      if (video.getScriptId() == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "해당 영상에 연결된 스크립트가 없습니다: " + videoId);
      }
      
      EducationScript script = scriptRepository.findById(video.getScriptId())
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + video.getScriptId()));
      
      return new com.ctrlf.education.script.dto.EducationScriptDto.ScriptLookupResponse(
          script.getId(),
          script.getEducationId(),
          videoId,
          script.getTitle(),
          script.getVersion(),
          script.getStatus()
      );
    }
    
    // 2. educationId로 조회
    if (educationId != null) {
      List<EducationScript> scripts = scriptRepository
          .findByEducationIdAndDeletedAtIsNullOrderByVersionDesc(educationId);
      
      if (scripts.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "해당 교육에 연결된 스크립트가 없습니다: " + educationId);
      }
      
      EducationScript latestScript = scripts.get(0); // 최신 버전
      
      // videoId 찾기 (있으면)
      UUID foundVideoId = videoRepository.findAll().stream()
          .filter(v -> latestScript.getId().equals(v.getScriptId()))
          .map(EducationVideo::getId)
          .findFirst()
          .orElse(null);
      
      return new com.ctrlf.education.script.dto.EducationScriptDto.ScriptLookupResponse(
          latestScript.getId(),
          latestScript.getEducationId(),
          foundVideoId,
          latestScript.getTitle(),
          latestScript.getVersion(),
          latestScript.getStatus()
      );
    }
    
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "videoId 또는 educationId 중 하나는 필수입니다");
  }

  /**
   * 스크립트의 렌더 스펙 조회 (AI 서버 내부 API용).
   * 스크립트의 챕터/씬 정보를 렌더링에 필요한 형태로 변환합니다.
   *
   * @param scriptId 스크립트 ID
   * @return 렌더 스펙 응답
   */
  @Transactional(readOnly = true)
  public com.ctrlf.education.script.dto.EducationScriptDto.RenderSpecResponse getRenderSpec(UUID scriptId) {
    EducationScript script = scriptRepository.findById(scriptId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + scriptId));

    // videoId 찾기 (sourceSetId 통해 또는 직접 연결)
    String videoIdStr = null;
    if (script.getSourceSetId() != null) {
      videoIdStr = videoRepository.findAll().stream()
          .filter(v -> script.getId().equals(v.getScriptId()))
          .map(v -> v.getId().toString())
          .findFirst()
          .orElse(null);
    }
    if (videoIdStr == null) {
      // scriptId로 직접 연결된 video 찾기
      videoIdStr = videoRepository.findByScriptId(scriptId).stream()
          .findFirst()
          .map(v -> v.getId().toString())
          .orElse("");
    }

    // 챕터/씬 조회 및 렌더 스펙으로 변환
    List<EducationScriptChapter> chapters = 
        chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId);
    
    List<com.ctrlf.education.script.dto.EducationScriptDto.RenderSceneItem> scenes = new ArrayList<>();
    int sceneOrder = 1;
    
    for (EducationScriptChapter chapter : chapters) {
      List<EducationScriptScene> chapterScenes = 
          sceneRepository.findByChapterIdOrderBySceneIndexAsc(chapter.getId());
      
      for (EducationScriptScene scene : chapterScenes) {
        // visual 필드를 VisualSpec으로 변환
        com.ctrlf.education.script.dto.EducationScriptDto.RenderVisualSpec visualSpec = 
            new com.ctrlf.education.script.dto.EducationScriptDto.RenderVisualSpec(
                "TEXT_HIGHLIGHT",
                scene.getVisual(),
                new ArrayList<>()
            );
        
        scenes.add(new com.ctrlf.education.script.dto.EducationScriptDto.RenderSceneItem(
            scene.getId().toString(),
            sceneOrder++,
            chapter.getTitle(),
            scene.getPurpose(),
            scene.getNarration(),
            scene.getCaption(),
            scene.getDurationSec() != null ? scene.getDurationSec().doubleValue() : 0.0,
            visualSpec
        ));
      }
    }

    return new com.ctrlf.education.script.dto.EducationScriptDto.RenderSpecResponse(
        scriptId.toString(),
        videoIdStr,
        script.getTitle(),
        script.getTotalDurationSec() != null ? script.getTotalDurationSec().doubleValue() : 0.0,
        scenes
    );
  }

  @Transactional
  public void deleteScript(UUID scriptId) {
    EducationScript script =
        scriptRepository
            .findById(scriptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + scriptId));
    
    // 1. 스크립트 씬 삭제 (chapter_id를 참조하므로 chapter보다 먼저 삭제)
    List<EducationScriptScene> scenes = sceneRepository.findByScriptIdOrderByChapterIdAscSceneIndexAsc(scriptId);
    if (!scenes.isEmpty()) {
      sceneRepository.deleteAll(scenes);
      log.info("스크립트 씬 삭제 완료. scriptId={}, count={}", scriptId, scenes.size());
    }
    
    // 2. 스크립트 챕터 삭제 (script_id를 참조하므로 script보다 먼저 삭제)
    List<EducationScriptChapter> chapters = chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId);
    if (!chapters.isEmpty()) {
      chapterRepository.deleteAll(chapters);
      log.info("스크립트 챕터 삭제 완료. scriptId={}, count={}", scriptId, chapters.size());
    }
    
    // 3. 스크립트 삭제
    scriptRepository.delete(script);
    log.info("스크립트 삭제 완료. scriptId={}", scriptId);
  }

  @Transactional
  public ScriptUpdateResponse updateScript(UUID scriptId, ScriptUpdateRequest request) {
    EducationScript script =
        scriptRepository
            .findById(scriptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + scriptId));

    if (request.script() != null) {
      script.setRawPayload(request.script());
      script.setVersion(script.getVersion() == null ? 2 : script.getVersion() + 1);
      scriptRepository.save(script);
    }

    if (request.chapters() != null) {
      List<EducationScriptScene> oldScenes =
          sceneRepository.findByScriptIdOrderByChapterIdAscSceneIndexAsc(scriptId);
      if (!oldScenes.isEmpty()) {
        sceneRepository.deleteAll(oldScenes);
      }
      List<EducationScriptChapter> oldChapters =
          chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId);
      if (!oldChapters.isEmpty()) {
        chapterRepository.deleteAll(oldChapters);
      }

      for (ChapterUpsert cu : request.chapters()) {
        EducationScriptChapter ch = new EducationScriptChapter();
        ch.setScriptId(scriptId);
        ch.setChapterIndex(cu.index());
        ch.setTitle(cu.title());
        ch.setDurationSec(cu.durationSec());
        chapterRepository.save(ch);

        if (cu.scenes() != null) {
          for (SceneUpsert su : cu.scenes()) {
            EducationScriptScene sc = new EducationScriptScene();
            sc.setScriptId(scriptId);
            sc.setChapterId(ch.getId());
            sc.setSceneIndex(su.index());
            sc.setPurpose(su.purpose());
            sc.setNarration(su.narration());
            sc.setCaption(su.caption());
            sc.setVisual(su.visual());
            sc.setDurationSec(su.durationSec());
            sc.setSourceChunkIndexes(su.sourceChunkIndexes());
            sc.setConfidenceScore(su.confidenceScore());
            sceneRepository.save(sc);
          }
        }
      }
    }

    log.info("스크립트 수정 완료. scriptId={}, newVersion={}", scriptId, script.getVersion());
    return new ScriptUpdateResponse(true, scriptId);
  }

  @Transactional
  public ScriptCompleteResponse handleScriptComplete(ScriptCompleteCallback callback) {
    // EducationVideo 조회
    EducationVideo video = videoRepository.findById(callback.videoId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상 컨텐츠를 찾을 수 없습니다: " + callback.videoId()));

    // script Object를 JSON 문자열로 변환
    String scriptJson;
    try {
      scriptJson = objectMapper.writeValueAsString(callback.script());
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스크립트 JSON 변환 실패: " + e.getMessage());
    }

    // 기존 스크립트가 있으면 소프트 삭제 및 버전 가져오기
    int newVersion = 1;
    if (video.getScriptId() != null) {
      EducationScript oldScript = scriptRepository.findById(video.getScriptId()).orElse(null);
      if (oldScript != null) {
        // 이전 스크립트 소프트 삭제
        oldScript.setDeletedAt(Instant.now());
        scriptRepository.save(oldScript);
        // 버전 증가
        newVersion = (oldScript.getVersion() != null ? oldScript.getVersion() : 0) + 1;
        log.info("이전 스크립트 소프트 삭제. oldScriptId={}, oldVersion={}", 
            oldScript.getId(), oldScript.getVersion());
      }
    }

    // 새 스크립트 생성 (ID는 수동 생성)
    // NOTE: materialId는 EducationVideo에 이미 저장되어 있음
    EducationScript script = new EducationScript();
    script.setId(UUID.randomUUID()); // UUID 수동 생성
    script.setEducationId(video.getEducationId());
    script.setRawPayload(scriptJson);
    script.setVersion(newVersion);
    scriptRepository.save(script);

    // EducationVideo에 scriptId 연결 및 상태 업데이트
    video.setScriptId(script.getId());
    video.setStatus("SCRIPT_READY"); // 스크립트 생성 완료 → 영상 생성 대기
    videoRepository.save(video);

    log.info(
        "스크립트 생성 완료 콜백 처리. materialId={}, videoId={}, scriptId={}, version={}",
        video.getMaterialId(),
        callback.videoId(),
        script.getId(),
        newVersion);
    return new ScriptCompleteResponse(true, script.getId());
  }

  /**
   * SourceSet 완료 콜백에서 받은 스크립트를 저장 (멀티문서 지원).
   * 
   * @param sourceSetId 소스셋 ID
   * @param script 스크립트 데이터
   * @return 저장된 스크립트 ID
   */
  @Transactional
  public UUID saveScriptFromSourceSet(
      UUID sourceSetId,
      com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteCallback.SourceSetScript script
  ) {
    // 소스셋 조회
    SourceSet sourceSet = sourceSetRepository
        .findByIdAndNotDeleted(sourceSetId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "소스셋을 찾을 수 없습니다: " + sourceSetId));

    // 교육 ID 확인
    UUID educationId = UUID.fromString(script.educationId());

    // 스크립트 엔티티 생성
    EducationScript scriptEntity = new EducationScript();
    
    // FastAPI에서 scriptId를 제공한 경우 해당 ID 사용, 없으면 새 UUID 생성
    // UUID는 중복 가능성이 거의 없으므로 제공된 ID를 그대로 사용
    UUID scriptId;
    if (script.scriptId() != null && !script.scriptId().isBlank()) {
      try {
        scriptId = UUID.fromString(script.scriptId());
        log.info("FastAPI 제공 scriptId 사용: {}", scriptId);
      } catch (IllegalArgumentException e) {
        log.warn("잘못된 scriptId 형식, 새 UUID 생성: scriptId={}", script.scriptId());
        scriptId = UUID.randomUUID();
      }
    } else {
      scriptId = UUID.randomUUID();
      log.debug("scriptId 미제공, 새 UUID 생성: {}", scriptId);
    }
    scriptEntity.setId(scriptId);
    
    scriptEntity.setEducationId(educationId);
    scriptEntity.setSourceSetId(sourceSetId);
    scriptEntity.setTitle(script.title());
    scriptEntity.setTotalDurationSec(script.totalDurationSec());
    scriptEntity.setVersion(script.version());
    scriptEntity.setLlmModel(script.llmModel());
    scriptEntity.setStatus("DRAFT"); // 초기 상태
    // rawPayload는 전체 스크립트 JSON으로 저장
    try {
      String rawPayload = objectMapper.writeValueAsString(script);
      scriptEntity.setRawPayload(rawPayload);
    } catch (JsonProcessingException e) {
      log.warn("스크립트 JSON 직렬화 실패, 빈 문자열로 저장: error={}", e.getMessage());
      scriptEntity.setRawPayload("{}");
    }
    scriptEntity = scriptRepository.save(scriptEntity);
    
    // 저장 확인: 실제로 DB에 저장되었는지 확인
    EducationScript savedScript = scriptRepository.findById(scriptEntity.getId()).orElse(null);
    if (savedScript == null) {
      log.error("스크립트 저장 실패: scriptId={}가 DB에 저장되지 않음", scriptEntity.getId());
      throw new IllegalStateException("스크립트 저장 실패: " + scriptEntity.getId());
    }
    log.debug("스크립트 저장 확인: scriptId={}, title={}", savedScript.getId(), savedScript.getTitle());

    // 챕터 및 씬 저장
    try {
      if (script.chapters() != null) {
        log.debug("챕터 저장 시작: chapterCount={}", script.chapters().size());
        for (var chapterData : script.chapters()) {
          EducationScriptChapter chapter = new EducationScriptChapter();
          chapter.setScriptId(scriptEntity.getId());
          chapter.setChapterIndex(chapterData.chapterIndex());
          chapter.setTitle(chapterData.title());
          chapter.setDurationSec(chapterData.durationSec());
          chapter = chapterRepository.save(chapter);
          log.debug("챕터 저장 완료: chapterId={}, chapterIndex={}", chapter.getId(), chapter.getChapterIndex());

          // 씬 저장
          if (chapterData.scenes() != null) {
            log.debug("씬 저장 시작: sceneCount={}", chapterData.scenes().size());
            for (var sceneData : chapterData.scenes()) {
              EducationScriptScene scene = new EducationScriptScene();
              scene.setScriptId(scriptEntity.getId());
              scene.setChapterId(chapter.getId());
              scene.setSceneIndex(sceneData.sceneIndex());
              scene.setPurpose(sceneData.purpose());
              scene.setNarration(sceneData.narration());
              scene.setCaption(sceneData.caption());
              scene.setVisual(sceneData.visual());
              scene.setDurationSec(sceneData.durationSec());
              scene.setConfidenceScore(sceneData.confidenceScore());
              
              // sourceRefs를 JSON 문자열로 저장
              if (sceneData.sourceRefs() != null && !sceneData.sourceRefs().isEmpty()) {
                try {
                  String sourceRefsJson = objectMapper.writeValueAsString(sceneData.sourceRefs());
                  scene.setSourceRefs(sourceRefsJson);
                } catch (JsonProcessingException e) {
                  log.warn("sourceRefs JSON 직렬화 실패: error={}", e.getMessage());
                }
              }
              
              sceneRepository.save(scene);
              log.debug("씬 저장 완료: sceneIndex={}", sceneData.sceneIndex());
            }
          }
        }
        log.info("챕터 및 씬 저장 완료: scriptId={}", scriptEntity.getId());
      }
    } catch (Exception e) {
      log.error("챕터/씬 저장 중 예외 발생: scriptId={}, error={}", scriptEntity.getId(), e.getMessage(), e);
      throw e; // 예외를 다시 던져서 트랜잭션 롤백
    }

    // 최종 저장 확인: 트랜잭션 커밋 전 최종 확인
    UUID finalScriptId = scriptEntity.getId();
    EducationScript finalCheck = scriptRepository.findById(finalScriptId).orElse(null);
    if (finalCheck == null) {
      log.error("스크립트 최종 확인 실패: scriptId={}가 DB에서 찾을 수 없음", finalScriptId);
    } else {
      log.debug("스크립트 최종 확인 성공: scriptId={}, title={}", finalCheck.getId(), finalCheck.getTitle());
    }
    
    log.info("SourceSet 스크립트 저장 완료: sourceSetId={}, scriptId={}, educationId={}", 
        sourceSetId, finalScriptId, educationId);
    
    return finalScriptId;
  }
}
