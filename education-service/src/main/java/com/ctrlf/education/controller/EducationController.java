package com.ctrlf.education.controller;

import com.ctrlf.education.dto.CreateEducationRequest;
import com.ctrlf.education.dto.CreateEducationResponse;
import com.ctrlf.education.dto.MandatoryEducationDto;
import com.ctrlf.education.service.EducationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping
public class EducationController {

    private final EducationService educationService;

    public EducationController(EducationService educationService) {
        this.educationService = educationService;
    }

    @PostMapping("/edu")
    public ResponseEntity<CreateEducationResponse> createEducation(@jakarta.validation.Valid @RequestBody CreateEducationRequest req) {
        UUID id = educationService.createEducation(req);
        return ResponseEntity
            .created(URI.create("/edu/" + id))
            .body(new CreateEducationResponse(id, "CREATED"));
    }

    @GetMapping("/edus")
    public ResponseEntity<List<MandatoryEducationDto>> getEducations(
        @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
        @RequestParam(name = "size", required = false, defaultValue = "10") Integer size,
        @RequestParam(name = "completed", required = false) Boolean completed,
        @RequestParam(name = "year", required = false) Integer year,
        @RequestParam(name = "category", required = false) String category,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Optional<UUID> userUuid = Optional.empty();
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            try { userUuid = Optional.of(UUID.fromString(jwt.getSubject())); } catch (IllegalArgumentException ignored) {}
        }
        List<MandatoryEducationDto> result = educationService.getEducations(
            page, size, Optional.ofNullable(completed), Optional.ofNullable(year), Optional.ofNullable(category), userUuid
        );
        return ResponseEntity.ok(result);
    }
}


