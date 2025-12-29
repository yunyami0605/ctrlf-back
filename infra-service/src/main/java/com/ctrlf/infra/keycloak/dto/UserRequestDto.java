package com.ctrlf.infra.keycloak.dto;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class UserRequestDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateUserReq {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Boolean enabled;
        private Map<String, Object> attributes;
        private String initialPassword;
        private Boolean temporaryPassword;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateUserReq {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Boolean enabled;
        private Map<String, Object> attributes;
        private List<String> roleNames; // 역할도 함께 수정 가능
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PasswordReq {
        private String value;
        private Boolean temporary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRolesReq {
        private List<String> roleNames;
    }
}
