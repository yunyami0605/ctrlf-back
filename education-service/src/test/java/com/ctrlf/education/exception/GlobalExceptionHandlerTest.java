package com.ctrlf.education.exception;

import com.ctrlf.common.dto.ApiError;
import com.ctrlf.common.exception.BusinessException;
import com.ctrlf.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

/**
 * GlobalExceptionHandler 단위 테스트.
 */
@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    /**
     * 생성된 ResponseEntity의 상태 코드, 본문 내용을 검증
     */
    @Test
    @DisplayName("EducationNotFoundException 처리 - 404")
    void handleEducationNotFound() {
        // given
        UUID educationId = UUID.randomUUID();
        EducationNotFoundException ex = new EducationNotFoundException(educationId);

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handleEntityNotFound(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getMessage()).contains("Education");
    }

    @Test
    @DisplayName("BusinessException 처리")
    void handleBusinessException() {
        // given
        BusinessException ex = new BusinessException(HttpStatus.BAD_REQUEST, "비즈니스 오류");

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handleBusinessException(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("비즈니스 오류");
    }

    @Test
    @DisplayName("ValidationException 처리 - 400")
    void handleValidationException() {
        // given
        ValidationException ex = new ValidationException("검증 실패");

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handleValidationException(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("검증 실패");
    }

    @Test
    @DisplayName("ResponseStatusException 처리")
    void handleResponseStatusException() {
        // given
        ResponseStatusException ex = new ResponseStatusException(
            HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"
        );

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handle(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("리소스를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("IllegalArgumentException 처리 - 400")
    void handleIllegalArgumentException() {
        // given
        IllegalArgumentException ex = new IllegalArgumentException("잘못된 인자");

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handleBadRequest(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("잘못된 인자");
    }

    @Test
    @DisplayName("Exception 처리 - 500")
    void handleUnexpectedException() {
        // given
        Exception ex = new RuntimeException("예상치 못한 오류");

        // when
        ResponseEntity<ApiError> response = exceptionHandler.handleUnexpected(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
    }
}
