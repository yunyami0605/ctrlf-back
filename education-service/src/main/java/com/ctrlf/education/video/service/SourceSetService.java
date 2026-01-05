package com.ctrlf.education.video.service;

import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.script.service.ScriptService;
import com.ctrlf.education.video.client.SourceSetAiClient;
import com.ctrlf.education.video.client.SourceSetAiDtos;
import com.ctrlf.education.video.dto.VideoDtos.InternalSourceSetDocumentsResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCompleteResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCreateRequest;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetCreateResponse;
import com.ctrlf.education.video.dto.VideoDtos.SourceSetUpdateRequest;
import com.ctrlf.education.video.entity.SourceSet;
import com.ctrlf.education.video.entity.SourceSetDocument;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import com.ctrlf.education.video.repository.SourceSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 소스셋(SourceSet) 서비스.
 * 여러 문서를 묶어 스크립트/영상 제작 단위를 관리합니다.
 */
@Service
@RequiredArgsConstructor
public class SourceSetService {

    private static final Logger log = LoggerFactory.getLogger(SourceSetService.class);

    private final SourceSetRepository sourceSetRepository;
    private final SourceSetDocumentRepository sourceSetDocumentRepository;
    private final SourceSetAiClient sourceSetAiClient;
    private final EducationVideoRepository videoRepository;
    private final EducationRepository educationRepository;
    private final InfraRagClient infraRagClient;
    private final ScriptService scriptService;
    private final ObjectMapper objectMapper;

    @Value("${dev.auto-source-set-callback:false}")
    private boolean autoSourceSetCallback;

    /**
     * 소스셋 생성.
     * 
     * @param req 소스셋 생성 요청
     * @param userUuid 요청자 UUID (JWT에서 추출)
     */
    @Transactional
    public SourceSetCreateResponse createSourceSet(SourceSetCreateRequest req, UUID userUuid) {
        // videoId 유효성 검증 (필수)
        if (!videoRepository.existsById(req.videoId())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "영상을 찾을 수 없습니다: " + req.videoId());
        }

        // educationId 유효성 검증 (필수)
        if (!educationRepository.existsById(req.educationId())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "교육을 찾을 수 없습니다: " + req.educationId());
        }

        // 소스셋 생성 (requestedBy는 JWT에서 추출한 userUuid 사용)
        SourceSet sourceSet = SourceSet.create(
            req.title(),
            req.domain(),
            userUuid.toString(), // JWT에서 추출한 사용자 UUID
            req.educationId(),   // 필수
            req.videoId()        // 필수
        );
        sourceSet = sourceSetRepository.save(sourceSet);

        // 문서 관계 추가
        List<SourceSetDocument> documents = new ArrayList<>();
        for (String documentIdStr : req.documentIds()) {
            try {
                UUID documentId = UUID.fromString(documentIdStr);
                SourceSetDocument ssd = SourceSetDocument.create(sourceSet, documentId);
                documents.add(ssd);
            } catch (IllegalArgumentException e) {
                log.warn("잘못된 documentId 형식: {}", documentIdStr);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "잘못된 documentId 형식: " + documentIdStr);
            }
        }
        sourceSetDocumentRepository.saveAll(documents);

        // 소스셋 생성 후 AI 서버에 작업 시작 요청 (BestEffort)
        // 트랜잭션 커밋 후 실행하여 SourceSet이 DB에 확실히 저장된 후 AI 서버가 조회할 수 있도록 함
        final UUID sourceSetId = sourceSet.getId();
        final UUID educationId = sourceSet.getEducationId();
        final UUID videoId = sourceSet.getVideoId();
        final List<String> documentIdsForCallback = req.documentIds();
        final String sourceSetTitle = sourceSet.getTitle();
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (autoSourceSetCallback) {
                    // 개발 환경: 자동 콜백 시뮬레이션 (비동기 실행)
                    log.info("[DEV] 자동 콜백 시뮬레이션 활성화: sourceSetId={}", sourceSetId);
                    // 새로운 트랜잭션에서 실행되도록 별도 메서드로 분리
                    try {
                        simulateSourceSetCompleteCallback(sourceSetId, videoId, educationId, documentIdsForCallback, sourceSetTitle);
                    } catch (Exception e) {
                        log.error("[DEV] 소스셋 완료 콜백 시뮬레이션 실패 (소스셋은 생성됨): sourceSetId={}, error={}", 
                            sourceSetId, e.getMessage(), e);
                        // 실패해도 소스셋 생성은 성공으로 처리 (이미 커밋됨)
                    }
                } else {
                    // 프로덕션: AI 서버에 작업 시작 요청
                    try {
                        log.info("트랜잭션 커밋 완료, AI 서버에 작업 시작 요청: sourceSetId={}", sourceSetId);
                        // 트랜잭션 밖에서 실행되므로 SourceSet을 다시 조회
                        SourceSet sourceSetAfterCommit = sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
                            .orElseThrow(() -> new IllegalStateException("SourceSet not found after commit: " + sourceSetId));
                        startSourceSetProcessing(sourceSetId, sourceSetAfterCommit, documentIdsForCallback);
                    } catch (Exception e) {
                        log.warn("소스셋 작업 시작 요청 실패 (소스셋은 생성됨): sourceSetId={}, error={}", 
                            sourceSetId, e.getMessage(), e);
                        // 실패해도 소스셋 생성은 성공으로 처리
                    }
                }
            }
        });

        // 응답 생성
        List<String> documentIds = req.documentIds();
        return new SourceSetCreateResponse(
            sourceSet.getId().toString(),
            sourceSet.getStatus(),
            documentIds
        );
    }

    /**
     * 소스셋 작업 시작 (AI 서버 호출) - v2.1 스펙.
     * 
     * <p>변경 사항:
     * <ul>
     *   <li>documents[] 제거: FastAPI가 Spring의 /internal/source-sets/{sourceSetId}/documents를 호출하여 조회</li>
     *   <li>datasetId, indexVersion, ingestId 제거: Spring이 미리 알 수 없음</li>
     *   <li>scriptJobId, domain, language 제거: 스펙에서 제거됨</li>
     *   <li>educationId 추가: SourceSet에서 가져옴</li>
     * </ul>
     * 
     * @param sourceSetId 소스셋 ID
     * @param sourceSet 소스셋 엔티티
     * @param documentIds 문서 ID 목록 (사용하지 않음, 참고용)
     */
    private void startSourceSetProcessing(
        UUID sourceSetId,
        SourceSet sourceSet,
        List<String> documentIds
    ) {
        // videoId 사용: SourceSet에 저장된 videoId 사용 (필수)
        String videoId = sourceSet.getVideoId().toString();
        
        // educationId 사용: SourceSet에 저장된 educationId 사용 (선택)
        UUID educationId = sourceSet.getEducationId();
        
        // AI 서버에 작업 시작 요청 (v2.1 스펙)
        // FastAPI가 문서 목록을 Spring에서 조회하므로 documents[]는 전송하지 않음
        UUID requestId = UUID.randomUUID();
        String traceId = "trace-" + sourceSetId.toString();
        SourceSetAiDtos.StartRequest startRequest = new SourceSetAiDtos.StartRequest(
            educationId, // 선택
            videoId,     // 필수
            requestId,   // requestId (멱등 키)
            traceId,     // traceId
            null,        // scriptPolicyId (선택)
            null         // llmModelHint (선택)
        );

        // 요청 로그 (전체 요청 body 포함)
        try {
            String requestJson = objectMapper.writeValueAsString(startRequest);
            log.info("소스셋 작업 시작 요청: sourceSetId={}, request={}", sourceSetId, requestJson);
        } catch (Exception e) {
            log.warn("요청 로그 직렬화 실패: sourceSetId={}, error={}", sourceSetId, e.getMessage());
            log.info("소스셋 작업 시작 요청: sourceSetId={}, videoId={}, educationId={}, requestId={}, traceId={}", 
                sourceSetId, videoId, educationId, requestId, traceId);
        }

        try {
            SourceSetAiDtos.StartResponse response = sourceSetAiClient.startSourceSet(
                sourceSetId.toString(),
                startRequest
            );
            
            // 응답 로그 (전체 응답 body 포함)
            try {
                String responseJson = objectMapper.writeValueAsString(response);
                if (response != null && response.received()) {
                    log.info("소스셋 작업 시작 요청 성공: sourceSetId={}, response={}", 
                        sourceSetId, responseJson);
                } else {
                    log.warn("소스셋 작업 시작 요청 실패: sourceSetId={}, response={}", 
                        sourceSetId, responseJson);
                }
            } catch (Exception e) {
                log.warn("응답 로그 직렬화 실패: sourceSetId={}, error={}", sourceSetId, e.getMessage());
                if (response != null && response.received()) {
                    log.info("소스셋 작업 시작 요청 성공: sourceSetId={}, status={}",
                        sourceSetId, response.status());
                } else {
                    log.warn("소스셋 작업 시작 요청 실패: sourceSetId={}, response={}", 
                        sourceSetId, response);
                }
            }
            
            // 스펙에 따르면 FastAPI가 콜백으로 결과를 알려주므로, 여기서는 요청만 보냅니다.
        } catch (RestClientException e) {
            log.error("소스셋 작업 시작 요청 중 네트워크/서버 오류: sourceSetId={}, error={}, message={}",
                sourceSetId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 소스셋 문서 변경 (추가/제거).
     * LOCKED 상태인 경우 변경 불가.
     */
    @Transactional
    public SourceSetCreateResponse updateSourceSetDocuments(UUID sourceSetId, SourceSetUpdateRequest req) {
        SourceSet sourceSet = sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "소스셋을 찾을 수 없습니다: " + sourceSetId));

        // LOCKED 상태 체크
        if ("LOCKED".equals(sourceSet.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "LOCKED 상태의 소스셋은 변경할 수 없습니다");
        }

        // 문서 제거
        if (req.removeDocumentIds() != null && !req.removeDocumentIds().isEmpty()) {
            List<UUID> removeIds = req.removeDocumentIds().stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
            
            List<SourceSetDocument> toRemove = sourceSetDocumentRepository
                .findBySourceSetId(sourceSetId).stream()
                .filter(ssd -> removeIds.contains(ssd.getDocumentId()))
                .collect(Collectors.toList());
            
            sourceSetDocumentRepository.deleteAll(toRemove);
        }

        // 문서 추가
        if (req.addDocumentIds() != null && !req.addDocumentIds().isEmpty()) {
            List<SourceSetDocument> toAdd = new ArrayList<>();
            for (String documentIdStr : req.addDocumentIds()) {
                try {
                    UUID documentId = UUID.fromString(documentIdStr);
                    // 중복 체크
                    boolean exists = sourceSetDocumentRepository
                        .findBySourceSetId(sourceSetId).stream()
                        .anyMatch(ssd -> ssd.getDocumentId().equals(documentId));
                    
                    if (!exists) {
                        SourceSetDocument ssd = SourceSetDocument.create(sourceSet, documentId);
                        toAdd.add(ssd);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 documentId 형식: {}", documentIdStr);
                }
            }
            sourceSetDocumentRepository.saveAll(toAdd);
        }

        // 업데이트 시각 갱신
        sourceSet.setUpdatedAt(Instant.now());
        sourceSetRepository.save(sourceSet);

        // 현재 문서 목록 조회
        List<String> documentIds = sourceSetDocumentRepository
            .findBySourceSetId(sourceSetId).stream()
            .map(ssd -> ssd.getDocumentId().toString())
            .collect(Collectors.toList());

        return new SourceSetCreateResponse(
            sourceSet.getId().toString(),
            sourceSet.getStatus(),
            documentIds
        );
    }

    /**
     * 소스셋 조회.
     */
    public SourceSet getSourceSet(UUID sourceSetId) {
        return sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "소스셋을 찾을 수 없습니다: " + sourceSetId));
    }

    /**
     * 소스셋의 문서 ID 목록 조회.
     */
    public List<UUID> getDocumentIds(UUID sourceSetId) {
        return sourceSetDocumentRepository
            .findBySourceSetId(sourceSetId).stream()
            .map(SourceSetDocument::getDocumentId)
            .collect(Collectors.toList());
    }

    /**
     * 소스셋 문서 목록 조회 (내부 API - FastAPI가 호출).
     * 
     * @param sourceSetId 소스셋 ID
     * @return 문서 목록 응답
     */
    @Transactional(readOnly = true)
    public InternalSourceSetDocumentsResponse getSourceSetDocuments(UUID sourceSetId) {
        SourceSet sourceSet = sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "소스셋을 찾을 수 없습니다: " + sourceSetId));

        // 소스셋에 포함된 문서 ID 목록 조회
        List<SourceSetDocument> sourceSetDocuments = sourceSetDocumentRepository
            .findBySourceSetId(sourceSetId);
        
        // 각 문서의 상세 정보 조회 (infra-service에서)
        List<InternalSourceSetDocumentsResponse.InternalDocumentItem> documentItems = new ArrayList<>();
        for (SourceSetDocument ssd : sourceSetDocuments) {
            try {
                InfraRagClient.DocumentInfoResponse docInfo = infraRagClient.getDocument(
                    ssd.getDocumentId().toString());
                
                if (docInfo != null) {
                    // S3 URL을 presigned URL로 변환 (FastAPI/RAGFlow가 접근 가능하도록)
                    String sourceUrl = infraRagClient.getPresignedDownloadUrl(docInfo.getSourceUrl());
                    documentItems.add(new InternalSourceSetDocumentsResponse.InternalDocumentItem(
                        docInfo.getId(),
                        docInfo.getTitle(),
                        docInfo.getDomain(),
                        sourceUrl,
                        docInfo.getStatus() != null ? docInfo.getStatus() : "QUEUED"
                    ));
                }
            } catch (RestClientException e) {
                log.warn("문서 정보 조회 실패: documentId={}, error={}", 
                    ssd.getDocumentId(), e.getMessage());
                // 개별 문서 조회 실패는 무시하고 계속 진행
            }
        }

        return new InternalSourceSetDocumentsResponse(
            sourceSetId.toString(),
            documentItems
        );
    }

    /**
     * 소스셋 완료 콜백 처리 (내부 API - FastAPI가 호출).
     * 
     * @param sourceSetId 소스셋 ID
     * @param callback 콜백 요청
     * @return 콜백 응답
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SourceSetCompleteResponse handleSourceSetComplete(
        UUID sourceSetId,
        SourceSetCompleteCallback callback
    ) {
        // 디버깅: SourceSet 존재 여부 확인 (삭제된 것 포함)
        sourceSetRepository.findById(sourceSetId).ifPresentOrElse(
            ss -> {
                if (ss.getDeletedAt() != null) {
                    log.warn(
                        "SourceSet이 삭제된 상태입니다: sourceSetId={}, deletedAt={}, status={}",
                        sourceSetId, ss.getDeletedAt(), ss.getStatus()
                    );
                } else {
                    log.debug("SourceSet 존재 확인: sourceSetId={}, status={}", sourceSetId, ss.getStatus());
                }
            },
            () -> log.warn("SourceSet이 DB에 존재하지 않습니다: sourceSetId={}", sourceSetId)
        );
        
        SourceSet sourceSet = sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
            .orElseThrow(() -> {
                log.error(
                    "소스셋 완료 콜백 실패: sourceSetId={}, status={}, sourceSetStatus={}, errorCode={}, errorMessage={}",
                    sourceSetId, callback.status(), callback.sourceSetStatus(),
                    callback.errorCode(), callback.errorMessage()
                );
                return new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "소스셋을 찾을 수 없습니다: " + sourceSetId);
            });

        // 소스셋 상태 업데이트
        sourceSet.setStatus(callback.sourceSetStatus());
        sourceSet.setUpdatedAt(Instant.now());
        
        // 실패 시 에러 정보 저장
        if ("FAILED".equals(callback.status()) || "FAILED".equals(callback.sourceSetStatus())) {
            sourceSet.setErrorCode(callback.errorCode());
            sourceSet.setFailReason(callback.errorMessage());
        } else {
            // 성공 시 에러 정보 초기화
            sourceSet.setErrorCode(null);
            sourceSet.setFailReason(null);
        }
        
        sourceSetRepository.save(sourceSet);

        // 콜백 상세 정보 로깅
        log.info(
            "=== 소스셋 완료 콜백 수신: sourceSetId={}, status={}, sourceSetStatus={}, errorCode={}, errorMessage={}, documents_count={} ===",
            sourceSetId, callback.status(), callback.sourceSetStatus(), 
            callback.errorCode(), callback.errorMessage(),
            callback.documents() != null ? callback.documents().size() : 0
        );
        
        // 문서별 상태 업데이트
        if (callback.documents() != null && !callback.documents().isEmpty()) {
            callback.documents().forEach(doc -> {
                try {
                    UUID documentId = UUID.fromString(doc.documentId());
                    sourceSetDocumentRepository.findBySourceSetIdAndDocumentId(sourceSetId, documentId)
                        .ifPresent(ssd -> {
                            if ("FAILED".equals(doc.status())) {
                                ssd.markFailed(doc.status(), doc.failReason());
                                log.warn(
                                    "문서 처리 실패: sourceSetId={}, docId={}, status={}, failReason={}",
                                    sourceSetId, doc.documentId(), doc.status(), doc.failReason()
                                );
                            } else if ("COMPLETED".equals(doc.status())) {
                                ssd.markCompleted();
                                log.debug(
                                    "문서 처리 성공: sourceSetId={}, docId={}, status={}",
                                    sourceSetId, doc.documentId(), doc.status()
                                );
                            }
                            sourceSetDocumentRepository.save(ssd);
                        });
                } catch (IllegalArgumentException e) {
                    log.warn("잘못된 documentId 형식: {}", doc.documentId());
                }
            });
        }

        // 성공 시 스크립트 저장 (전체 또는 패치)
        UUID scriptId = null;

        // 모드 1: 씬별 패치 전송 (SCRIPT_GENERATING 상태)
        if (callback.scriptPatch() != null) {
            try {
                UUID patchScriptId = scriptService.saveScriptPatchFromSourceSet(sourceSetId, callback.scriptPatch());
                log.info("스크립트 패치 저장 성공: sourceSetId={}, scriptId={}, progress={}/{}",
                    sourceSetId, patchScriptId,
                    callback.scriptPatch().currentScene(),
                    callback.scriptPatch().totalScenes());

                // 패치 모드에서는 EducationVideo에 scriptId 연결 및 상태 업데이트
                if (sourceSet.getVideoId() != null) {
                    final UUID finalScriptId = patchScriptId;
                    final int currentScene = callback.scriptPatch().currentScene();
                    final int totalScenes = callback.scriptPatch().totalScenes();
                    final boolean isComplete = currentScene >= totalScenes;
                    
                    videoRepository.findById(sourceSet.getVideoId()).ifPresent(video -> {
                        // 첫 패치에서만 scriptId 설정
                        if (video.getScriptId() == null) {
                            video.setScriptId(finalScriptId);
                            log.info("영상에 스크립트 연결 (패치 모드): videoId={}, scriptId={}",
                                video.getId(), finalScriptId);
                        }
                        
                        // 모든 씬이 완료되었는지 확인하여 상태 설정
                        if (isComplete) {
                            video.setStatus("SCRIPT_READY");
                            log.info("스크립트 생성 완료 (패치 모드): videoId={}, progress={}/{}",
                                video.getId(), currentScene, totalScenes);
                        } else {
                            video.setStatus("SCRIPT_GENERATING");
                            log.debug("스크립트 생성 중 (패치 모드): videoId={}, progress={}/{}",
                                video.getId(), currentScene, totalScenes);
                        }
                        videoRepository.save(video);
                    });
                }

                return new SourceSetCompleteResponse(true, patchScriptId);
            } catch (Exception e) {
                log.error("스크립트 패치 저장 실패: sourceSetId={}, error={}",
                    sourceSetId, e.getMessage(), e);
                // 패치 저장 실패는 경고로 처리 (다음 패치에서 재시도 가능)
                return new SourceSetCompleteResponse(false, null);
            }
        }

        // 모드 2: 전체 스크립트 전송 (기존 로직)
        // TODO 제거 예정
        if ("COMPLETED".equals(callback.status()) && callback.script() != null) {
            try {
                scriptId = saveScriptFromCallback(sourceSet, callback.script());
                log.info("소스셋 완료 콜백 처리 성공: sourceSetId={}, scriptId={}, status={}",
                    sourceSetId, scriptId, callback.sourceSetStatus());
            } catch (Exception e) {
                log.error("스크립트 저장 실패: sourceSetId={}, error={}",
                    sourceSetId, e.getMessage(), e);
                // 스크립트 저장 실패 시 에러 정보 저장
                sourceSet.setStatus("FAILED");
                sourceSet.setErrorCode("SCRIPT_SAVE_ERROR");
                sourceSet.setFailReason("스크립트 저장 실패: " + e.getMessage());
                sourceSetRepository.save(sourceSet);
                // 스크립트 저장 실패 시 HTTP 에러 상태 코드 반환
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    String.format("스크립트 저장 실패: sourceSetId=%s, error=%s", sourceSetId, e.getMessage())
                );
            }
        } else if (callback.scriptPatch() == null) {
            // scriptPatch도 없고 script도 없는 경우에만 실패 처리
            log.error(
                "=== 소스셋 완료 콜백 처리 (실패): sourceSetId={}, status={}, sourceSetStatus={}, errorCode={}, errorMessage={}, documents={} ===",
                sourceSetId, callback.status(), callback.sourceSetStatus(), 
                callback.errorCode(), callback.errorMessage(),
                callback.documents() != null ? callback.documents().size() : 0
            );
            
            // 에러 정보가 아직 설정되지 않은 경우 (예: status=COMPLETED but script=null)
            if (sourceSet.getErrorCode() == null) {
                sourceSet.setStatus("FAILED");
                if (callback.script() == null) {
                    sourceSet.setErrorCode("SCRIPT_NULL");
                    sourceSet.setFailReason("콜백에 스크립트 데이터가 없습니다");
                } else {
                    sourceSet.setErrorCode(callback.errorCode() != null ? callback.errorCode() : "UNKNOWN_ERROR");
                    sourceSet.setFailReason(callback.errorMessage() != null ? callback.errorMessage() : "알 수 없는 오류");
                }
                sourceSetRepository.save(sourceSet);
            }
            
            // 실패 상태일 때 HTTP 에러 상태 코드 반환
            String errorMessage = callback.errorMessage() != null ? callback.errorMessage() : "소스셋 처리 실패";
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format("소스셋 처리 실패: sourceSetId=%s, errorCode=%s, errorMessage=%s",
                    sourceSetId, callback.errorCode(), errorMessage)
            );
        }

        // 트랜잭션 커밋 전 최종 확인: scriptId가 실제로 저장되었는지 확인
        if (scriptId != null) {
            log.info("트랜잭션 커밋 전 스크립트 확인: scriptId={}", scriptId);
        }

        log.info("handleSourceSetComplete 메서드 완료, 트랜잭션 커밋 예정: scriptId={}", scriptId);
        return new SourceSetCompleteResponse(true, scriptId);
    }

    /**
     * 콜백에서 받은 스크립트를 DB에 저장.
     * 
     * @param sourceSet 소스셋 엔티티
     * @param script 스크립트 데이터
     * @return 생성된 스크립트 ID
     */
    private UUID saveScriptFromCallback(
        SourceSet sourceSet,
        SourceSetCompleteCallback.SourceSetScript script
    ) {
        UUID scriptId = scriptService.saveScriptFromSourceSet(sourceSet.getId(), script);
        
        // EducationVideo에 scriptId 연결 및 상태 업데이트
        if (sourceSet.getVideoId() != null) {
            videoRepository.findById(sourceSet.getVideoId()).ifPresent(video -> {
                video.setScriptId(scriptId);
                video.setStatus("SCRIPT_READY"); // 스크립트 생성 완료 → 영상 생성 대기
                videoRepository.save(video);
                log.info("영상에 스크립트 연결 완료: videoId={}, scriptId={}", video.getId(), scriptId);
            });
        }
        
        log.info("스크립트 저장 완료: sourceSetId={}, scriptId={}", sourceSet.getId(), scriptId);
        log.debug("saveScriptFromCallback 완료: scriptId={}, 트랜잭션 커밋 대기 중", scriptId);
        return scriptId;
    }

    /**
     * 개발 환경: 소스셋 완료 콜백 자동 시뮬레이션.
     * AI 서버 호출 없이 더미 데이터로 콜백을 처리합니다.
     * 
     * @param sourceSetId 소스셋 ID
     * @param videoId 영상 ID
     * @param educationId 교육 ID
     * @param documentIds 문서 ID 목록
     * @param title 소스셋 제목
     */
    private void simulateSourceSetCompleteCallback(
        UUID sourceSetId,
        UUID videoId,
        UUID educationId,
        List<String> documentIds,
        String title
    ) {
        log.info("[DEV] 소스셋 완료 콜백 시뮬레이션 시작: sourceSetId={}, videoId={}, educationId={}", 
            sourceSetId, videoId, educationId);

        // 문서별 결과 생성 (모두 COMPLETED)
        List<SourceSetCompleteCallback.DocumentResult> documentResults = documentIds.stream()
            .map(docId -> new SourceSetCompleteCallback.DocumentResult(
                docId,
                "COMPLETED",
                null // failReason
            ))
            .collect(Collectors.toList());

        // 더미 스크립트 생성
        UUID scriptId = UUID.randomUUID();
        List<SourceSetCompleteCallback.SourceSetScript.SourceSetChapter> chapters = new ArrayList<>();
        
        // 챕터 1: 개요
        List<SourceSetCompleteCallback.SourceSetScript.SourceSetScene> chapter1Scenes = new ArrayList<>();
        if (!documentIds.isEmpty()) {
            String firstDocId = documentIds.get(0);
            // SourceRef는 SourceSetScene 내부에 중첩되어 있으므로, SourceSetScene를 통해 생성
            SourceSetCompleteCallback.SourceSetScript.SourceSetScene.SourceRef sourceRef1 = 
                new SourceSetCompleteCallback.SourceSetScript.SourceSetScene.SourceRef(firstDocId, 0);
            chapter1Scenes.add(new SourceSetCompleteCallback.SourceSetScript.SourceSetScene(
                0, // sceneIndex
                "개요 소개", // purpose
                title + "에 대한 개요를 소개합니다.", // narration
                title + " 개요", // caption
                "텍스트 오버레이", // visual
                30, // durationSec
                0.95f, // confidenceScore
                List.of(sourceRef1) // sourceRefs
            ));
        }
        chapters.add(new SourceSetCompleteCallback.SourceSetScript.SourceSetChapter(
            0, // chapterIndex
            "1. 개요", // title
            30, // durationSec
            chapter1Scenes // scenes
        ));

        // 챕터 2: 본문
        List<SourceSetCompleteCallback.SourceSetScript.SourceSetScene> chapter2Scenes = new ArrayList<>();
        if (!documentIds.isEmpty()) {
            String firstDocId = documentIds.get(0);
            // SourceRef는 SourceSetScene 내부에 중첩되어 있으므로, SourceSetScene를 통해 생성
            SourceSetCompleteCallback.SourceSetScript.SourceSetScene.SourceRef sourceRef2 = 
                new SourceSetCompleteCallback.SourceSetScript.SourceSetScene.SourceRef(firstDocId, 1);
            chapter2Scenes.add(new SourceSetCompleteCallback.SourceSetScript.SourceSetScene(
                0, // sceneIndex
                "주요 내용 설명", // purpose
                title + "의 주요 내용을 상세히 설명합니다.", // narration
                title + " 주요 내용", // caption
                "슬라이드 전환", // visual
                180, // durationSec
                0.92f, // confidenceScore
                List.of(sourceRef2) // sourceRefs
            ));
        }
        chapters.add(new SourceSetCompleteCallback.SourceSetScript.SourceSetChapter(
            1, // chapterIndex
            "2. 주요 내용", // title
            180, // durationSec
            chapter2Scenes // scenes
        ));

        SourceSetCompleteCallback.SourceSetScript script = new SourceSetCompleteCallback.SourceSetScript(
            scriptId.toString(), // scriptId
            educationId.toString(), // educationId
            sourceSetId.toString(), // sourceSetId
            title + " - 자동 생성 스크립트", // title
            600, // totalDurationSec (10분)
            1, // version
            "dev-mock-model", // llmModel
            chapters // chapters
        );

        // 콜백 DTO 생성
        SourceSetCompleteCallback callback = new SourceSetCompleteCallback(
            videoId, // videoId
            "COMPLETED", // status
            "SCRIPT_READY", // sourceSetStatus
            documentResults, // documents
            script, // script (전체 스크립트)
            null, // scriptPatch (패치 모드 아님)
            null, // errorCode
            null, // errorMessage
            UUID.randomUUID(), // requestId
            "trace-dev-" + sourceSetId // traceId
        );

        // 콜백 처리: @Async 또는 별도 트랜잭션에서 실행되도록 보장
        log.info("[DEV] handleSourceSetComplete 호출 전: sourceSetId={}, callback.status={}, script.scriptId={}", 
            sourceSetId, callback.status(), callback.script() != null ? callback.script().scriptId() : "null");
        
        // 직접 트랜잭션 관리로 명시적으로 커밋 보장
        SourceSetCompleteResponse response;
        try {
            // @Transactional 메서드를 호출하면 새로운 트랜잭션이 시작되고 메서드 완료 시 커밋됨
            response = handleSourceSetComplete(sourceSetId, callback);
            
            // 트랜잭션 커밋 후 실제로 저장되었는지 확인
            if (response != null && response.scriptId() != null) {
                UUID savedScriptId = response.scriptId();
                log.info("[DEV] handleSourceSetComplete 호출 성공: sourceSetId={}, scriptId={}", 
                    sourceSetId, savedScriptId);
                
                // 트랜잭션 커밋 후 실제 DB에서 확인 (약간의 지연 후)
                try {
                    Thread.sleep(100); // 트랜잭션 커밋 완료 대기
                    var savedScript = scriptService.getScript(savedScriptId);
                    log.info("[DEV] 트랜잭션 커밋 후 스크립트 확인 성공: scriptId={}, title={}", 
                        savedScriptId, savedScript != null ? savedScript.title() : "null");
                } catch (Exception e) {
                    log.error("[DEV] 트랜잭션 커밋 후 스크립트 확인 실패: scriptId={}, error={}", 
                        savedScriptId, e.getMessage());
                }
            } else {
                log.warn("[DEV] handleSourceSetComplete 응답에 scriptId 없음: sourceSetId={}", sourceSetId);
            }
        } catch (Exception e) {
            log.error("[DEV] handleSourceSetComplete 호출 실패: sourceSetId={}, error={}", 
                sourceSetId, e.getMessage(), e);
            throw e;
        }
        
        log.info("[DEV] 소스셋 완료 콜백 시뮬레이션 완료: sourceSetId={}, scriptId={}", 
            sourceSetId, response != null ? response.scriptId() : scriptId);
    }
}
