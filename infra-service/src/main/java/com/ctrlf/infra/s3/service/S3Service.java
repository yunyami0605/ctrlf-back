package com.ctrlf.infra.s3.service;

import com.ctrlf.infra.config.metrics.CustomMetrics;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 Presigned URL 발급 서비스.
 * - 업로드/다운로드 URL 생성
 * - 파일 URL(s3://bucket/key) 생성
 */
@Service
public class S3Service {

    private final S3Presigner presigner;
    private final CustomMetrics customMetrics;
    
    @Value("${app.s3.bucket:}")
    private String defaultBucket;
    
    private final Duration ttl;
    private final Duration downloadTtl;

    public S3Service(
        S3Presigner presigner,
        CustomMetrics customMetrics,
        @Value("${app.s3.ttlSeconds:36000}") long ttlSeconds,
        @Value("${app.s3.downloadTtlSeconds:43200}") long downloadTtlSeconds
    ) {
        this.presigner = presigner;
        this.customMetrics = customMetrics;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.downloadTtl = Duration.ofSeconds(downloadTtlSeconds);
    }

    /**
     * 업로드용 Presigned URL 생성.
     *
     * @param type 업로드 카테고리 경로 prefix
     * @param filename 원본 파일명(확장자 추출)
     * @param contentType MIME 타입
     */
    public URL presignUpload(String type, String filename, String contentType) {
        S3Path path = S3Path.buildUploadPath(defaultBucket, type, filename);
        PutObjectRequest putReq = PutObjectRequest.builder()
            .bucket(path.bucket())
            .key(path.key())
            .contentType(contentType)
            .build();
        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(putReq)
            .build();
        URL url = presigner.presignPutObject(presignReq).url();
        
        // 메트릭 기록
        customMetrics.incrementS3PresignedUrlsGenerated("upload");
        
        return url;
    }

    /**
     * 업로드용 Presigned URL 생성 및 파일 URL 반환.
     * presignUpload()와 동일한 경로를 사용하여 fileUrl을 생성합니다.
     *
     * @param type 업로드 카테고리 경로 prefix
     * @param filename 원본 파일명(확장자 추출)
     * @param contentType MIME 타입
     * @return 업로드 URL과 파일 URL을 포함한 결과
     */
    public PresignUploadResult presignUploadWithFileUrl(String type, String filename, String contentType) {
        S3Path path = S3Path.buildUploadPath(defaultBucket, type, filename);
        PutObjectRequest putReq = PutObjectRequest.builder()
            .bucket(path.bucket())
            .key(path.key())
            .contentType(contentType)
            .build();
        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(putReq)
            .build();
        URL uploadUrl = presigner.presignPutObject(presignReq).url();
        String fileUrl = "s3://" + path.bucket() + "/" + path.key();
        
        // 메트릭 기록
        customMetrics.incrementS3PresignedUrlsGenerated("upload");
        
        return new PresignUploadResult(uploadUrl, fileUrl);
    }

    // (objectKey 지정형 presign은 education-service에서 object_key를 무시하고
    //  기존 /upload(type, filename) API를 사용하는 방식으로 처리)

    /**
     * Presign 업로드 결과 (URL과 파일 URL 포함).
     */
    public record PresignUploadResult(URL uploadUrl, String fileUrl) {}

    /**
     * 다운로드용 Presigned URL 생성.
     *
     * @param fileUrl s3://bucket/key 또는 key 문자열
     */
    public URL presignDownload(String fileUrl) {
        return presignDownload(fileUrl, downloadTtl);
    }

    /**
     * 다운로드용 Presigned URL 생성 (만료 시간 지정).
     *
     * @param fileUrl s3://bucket/key 또는 key 문자열
     * @param expiration 만료 시간
     */
    public URL presignDownload(String fileUrl, Duration expiration) {
        S3Path path = S3Path.fromUrl(fileUrl, defaultBucket);
        GetObjectRequest getReq = GetObjectRequest.builder()
            .bucket(path.bucket())
            .key(path.key())
            .build();
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .getObjectRequest(getReq)
            .build();
        URL url = presigner.presignGetObject(presignReq).url();
        
        // 메트릭 기록
        customMetrics.incrementS3PresignedUrlsGenerated("download");
        
        return url;
    }

    /**
     * 업로드 완료 후 저장될 S3 파일 URL 문자열 생성.
     *
     * @param type 경로 prefix
     * @param filename 원본 파일명
     * @return s3://bucket/key
     */
    public String buildFileUrl(String type, String filename) {
        S3Path path = S3Path.buildUploadPath(defaultBucket, type, filename);
        return "s3://" + path.bucket() + "/" + path.key();
    }

    /**
     * 내부 S3 경로 표현 및 파서.
     */
    record S3Path(String bucket, String key) {
        /**
         * s3 URL 또는 key를 파싱하여 bucket/key로 변환.
         */
        static S3Path fromUrl(String url, String fallbackBucket) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("fileUrl required");
            }
            if (url.startsWith("s3://")) {
                String rest = url.substring(5);
                int slash = rest.indexOf('/');
                if (slash < 1) throw new IllegalArgumentException("invalid s3 url");
                String bucket = rest.substring(0, slash);
                String key = rest.substring(slash + 1);
                return new S3Path(bucket, key);
            }
            // treat as key only
            if (fallbackBucket == null || fallbackBucket.isBlank()) {
                throw new IllegalArgumentException("bucket required");
            }
            return new S3Path(fallbackBucket, url);
        }

        /**
         * 업로드 대상 키 경로를 생성.
         * - {type}/{uuid}.{ext}
         */
        static S3Path buildUploadPath(String defaultBucket, String type, String filename) {
            if (defaultBucket == null || defaultBucket.isBlank()) {
                throw new IllegalArgumentException("bucket not configured");
            }
            String safeType = (type == null ? "misc" : type.trim()).replaceAll("[^a-zA-Z0-9_\\-/]", "_");
            String ext = "";
            String base = UUID.randomUUID().toString();
            System.out.println("[DEBUG] buildUploadPath: filename=" + filename + ", type=" + type);
            if (filename != null) {
                int dot = filename.lastIndexOf('.');
                if (dot > -1 && dot < filename.length() - 1) {
                    ext = filename.substring(dot);
                }
                System.out.println("[DEBUG] extracted ext=" + ext);
            } else {
                System.out.println("[DEBUG] filename is NULL!");
            }
            String key = safeType + "/" + base + ext;
            System.out.println("[DEBUG] final key=" + key);
            return new S3Path(defaultBucket, key);
        }
    }
}

