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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        // 실패해도 소스셋 생성은 성공으로 처리
        try {
            startSourceSetProcessing(sourceSet.getId(), sourceSet, req.documentIds());
        } catch (Exception e) {
            log.warn("소스셋 작업 시작 요청 실패 (소스셋은 생성됨): sourceSetId={}, error={}", 
                sourceSet.getId(), e.getMessage(), e);
            // 실패해도 소스셋 생성은 성공으로 처리
        }

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
                    documentItems.add(new InternalSourceSetDocumentsResponse.InternalDocumentItem(
                        docInfo.getId(),
                        docInfo.getTitle(),
                        docInfo.getDomain(),
                        docInfo.getSourceUrl(),
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
    @Transactional
    public SourceSetCompleteResponse handleSourceSetComplete(
        UUID sourceSetId,
        SourceSetCompleteCallback callback
    ) {
        SourceSet sourceSet = sourceSetRepository.findByIdAndNotDeleted(sourceSetId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "소스셋을 찾을 수 없습니다: " + sourceSetId));

        // 소스셋 상태 업데이트
        sourceSet.setStatus(callback.sourceSetStatus());
        sourceSet.setUpdatedAt(Instant.now());
        sourceSetRepository.save(sourceSet);

        // 콜백 상세 정보 로깅
        log.info(
            "=== 소스셋 완료 콜백 수신: sourceSetId={}, status={}, sourceSetStatus={}, errorCode={}, errorMessage={}, documents_count={} ===",
            sourceSetId, callback.status(), callback.sourceSetStatus(), 
            callback.errorCode(), callback.errorMessage(),
            callback.documents() != null ? callback.documents().size() : 0
        );
        
        // 문서별 상태 로깅
        if (callback.documents() != null && !callback.documents().isEmpty()) {
            callback.documents().forEach(doc -> {
                if ("FAILED".equals(doc.status())) {
                    log.warn(
                        "문서 처리 실패: sourceSetId={}, docId={}, status={}, failReason={}",
                        sourceSetId, doc.documentId(), doc.status(), doc.failReason()
                    );
                } else {
                    log.debug(
                        "문서 처리 성공: sourceSetId={}, docId={}, status={}",
                        sourceSetId, doc.documentId(), doc.status()
                    );
                }
            });
        }

        // 성공 시 스크립트 저장
        UUID scriptId = null;
        if ("COMPLETED".equals(callback.status()) && callback.script() != null) {
            try {
                scriptId = saveScriptFromCallback(sourceSet, callback.script());
                log.info("소스셋 완료 콜백 처리 성공: sourceSetId={}, scriptId={}, status={}", 
                    sourceSetId, scriptId, callback.sourceSetStatus());
            } catch (Exception e) {
                log.error("스크립트 저장 실패: sourceSetId={}, error={}", 
                    sourceSetId, e.getMessage(), e);
                // 스크립트 저장 실패해도 콜백은 성공으로 처리 (멱등)
            }
        } else {
            log.error(
                "=== 소스셋 완료 콜백 처리 (실패): sourceSetId={}, status={}, sourceSetStatus={}, errorCode={}, errorMessage={}, documents={} ===",
                sourceSetId, callback.status(), callback.sourceSetStatus(), 
                callback.errorCode(), callback.errorMessage(),
                callback.documents() != null ? callback.documents().size() : 0
            );
        }

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
        return scriptId;
    }
}
