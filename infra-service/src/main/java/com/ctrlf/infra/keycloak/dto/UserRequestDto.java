package com.ctrlf.infra.keycloak.dto;

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
    public static class PasswordReq {
        private String value;
        private Boolean temporary;
    }
}
