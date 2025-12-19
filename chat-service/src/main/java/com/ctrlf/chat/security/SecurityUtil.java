package com.ctrlf.chat.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 보안 관련 유틸리티 클래스
 * 
 * <p>인증된 사용자 정보를 추출하는 유틸리티 메서드를 제공합니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
public class SecurityUtil {

    /**
     * 현재 인증된 사용자의 UUID를 반환합니다.
     * 
     * <p>JWT 토큰의 subject(sub) 클레임 값을 사용자 UUID로 사용합니다.</p>
     * 
     * @return 사용자 UUID
     * @throws IllegalStateException 인증된 사용자 정보가 없을 경우
     */
    public static UUID getUserUuid() {
        Object principal =
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof Jwt jwt) {
            // JWT의 subject(sub) 클레임 값이 사용자 UUID
            return UUID.fromString(jwt.getSubject());
        }

        throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
    }
}
