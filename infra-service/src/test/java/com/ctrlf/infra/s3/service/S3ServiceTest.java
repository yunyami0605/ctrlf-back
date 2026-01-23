package com.ctrlf.infra.s3.service;

import com.ctrlf.infra.config.metrics.CustomMetrics;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S3Service 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3Service 테스트")
class S3ServiceTest {

    @Mock
    private S3Presigner presigner;

    @Mock
    private CustomMetrics customMetrics;

    private S3Service s3Service;

    private String testBucket;

    @BeforeEach
    void setUp() {
        testBucket = "test-bucket";
        
        // S3Service 생성 (생성자 주입)
        s3Service = new S3Service(
            presigner,
            customMetrics,
            36000L,  // ttlSeconds
            43200L   // downloadTtlSeconds
        );
        
        ReflectionTestUtils.setField(s3Service, "defaultBucket", testBucket);
    }

    @Test
    @DisplayName("Presigned 업로드 URL 생성 - 성공")
    void presignUpload_Success() throws Exception {
        // given
        String type = "docs";
        String filename = "test.pdf";
        String contentType = "application/pdf";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/docs/uuid.pdf?X-Amz-Algorithm=...");

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // when
        URL result = s3Service.presignUpload(type, filename, contentType);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Presigned 다운로드 URL 생성 - s3:// URL 형식")
    void presignDownload_S3UrlFormat() throws Exception {
        // given
        String fileUrl = "s3://test-bucket/docs/file.pdf";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/docs/file.pdf?X-Amz-Algorithm=...");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // when
        URL result = s3Service.presignDownload(fileUrl);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Presigned 다운로드 URL 생성 - key만 제공")
    void presignDownload_KeyOnly() throws Exception {
        // given
        String key = "docs/file.pdf";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/docs/file.pdf?X-Amz-Algorithm=...");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // when
        URL result = s3Service.presignDownload(key);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Presigned 다운로드 URL 생성 - 만료 시간 지정")
    void presignDownload_WithExpiration() throws Exception {
        // given
        String fileUrl = "s3://test-bucket/docs/file.pdf";
        Duration expiration = Duration.ofHours(12);
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/docs/file.pdf?X-Amz-Algorithm=...");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // when
        URL result = s3Service.presignDownload(fileUrl, expiration);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("파일 URL 생성 - 성공")
    void buildFileUrl_Success() {
        // given
        String type = "docs";
        String filename = "test.pdf";

        // when
        String result = s3Service.buildFileUrl(type, filename);

        // then
        assertThat(result).isNotNull();
        assertThat(result).startsWith("s3://");
        assertThat(result).contains(type);
    }

    @Test
    @DisplayName("Presigned 업로드 URL 및 파일 URL 생성 - 성공")
    void presignUploadWithFileUrl_Success() throws Exception {
        // given
        String type = "docs";
        String filename = "test.pdf";
        String contentType = "application/pdf";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/docs/uuid.pdf?X-Amz-Algorithm=...");

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);

        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // when
        S3Service.PresignUploadResult result = s3Service.presignUploadWithFileUrl(type, filename, contentType);

        // then
        assertThat(result).isNotNull();
        assertThat(result.uploadUrl()).isNotNull();
        assertThat(result.fileUrl()).isNotNull();
        assertThat(result.fileUrl()).startsWith("s3://");
    }
}
