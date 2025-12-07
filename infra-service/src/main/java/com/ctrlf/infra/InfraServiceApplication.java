package com.ctrlf.infra;

import com.ctrlf.common.security.SecurityConfig;
import com.ctrlf.infra.keycloak.KeycloakAdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties({KeycloakAdminProperties.class})
@Import(SecurityConfig.class)
public class InfraServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InfraServiceApplication.class, args);
    }
}