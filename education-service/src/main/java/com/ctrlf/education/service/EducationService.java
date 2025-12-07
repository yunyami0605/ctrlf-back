package com.ctrlf.education.service;

import com.ctrlf.education.dto.CreateEducationRequest;
import com.ctrlf.education.dto.MandatoryEducationDto;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.repository.EducationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EducationService {

    private final EducationRepository educationRepository;
    private final ObjectMapper objectMapper;

    public EducationService(EducationRepository educationRepository, ObjectMapper objectMapper) {
        this.educationRepository = educationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID createEducation(CreateEducationRequest req) {
        validateCreate(req);
        String departmentScopeJson = null;
        if (req.getDepartmentScope() != null) {
            try {
                departmentScopeJson = objectMapper.writeValueAsString(req.getDepartmentScope());
            } catch (JsonProcessingException ignored) {
            }
        }
        Education e = new Education();
        e.setTitle(req.getTitle());
        e.setCategory(req.getCategory());
        e.setDepartmentScope(departmentScopeJson);
        e.setDescription(req.getDescription());
        e.setPassScore(req.getPassScore());
        e.setPassRatio(req.getPassRatio());
        e.setRequire(req.getRequire());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return educationRepository.save(e).getId();
    }

    public List<MandatoryEducationDto> getEducations(
        Integer page,
        Integer size,
        Optional<Boolean> completedFilter,
        Optional<Integer> yearFilter,
        Optional<String> categoryFilter,
        Optional<UUID> userUuid
    ) {
        int pageSafe = page == null ? 0 : Math.max(page, 0);
        int sizeSafe = size == null ? 10 : Math.min(Math.max(size, 1), 100);
        int offset = pageSafe * sizeSafe;
        Optional<String> sanitizedCategory = categoryFilter
            .filter(StringUtils::hasText)
            .map(s -> s.trim().toUpperCase())
            .filter(s -> s.equals("MANDATORY") || s.equals("JOB") || s.equals("ETC"));

        Optional<Boolean> effectiveCompleted = userUuid.isPresent() ? completedFilter : Optional.empty();

        List<Object[]> rows = educationRepository.findEducationsNative(
            offset,
            sizeSafe,
            effectiveCompleted.orElse(null),
            yearFilter.orElse(null),
            sanitizedCategory.orElse(null),
            userUuid.orElse(null)
        );
        List<MandatoryEducationDto> result = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            UUID id = (UUID) r[0];
            String title = (String) r[1];
            Boolean required = (Boolean) r[2];
            Boolean isCompleted = (Boolean) r[3];
            result.add(new MandatoryEducationDto(id, title, isCompleted != null && isCompleted, required != null && required));
        }
        return result;
    }

    private void validateCreate(CreateEducationRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is required");
        if (!StringUtils.hasText(req.getTitle())) throw new IllegalArgumentException("title is required");
        if (!StringUtils.hasText(req.getCategory())) throw new IllegalArgumentException("category is required");
        if (req.getRequire() == null) throw new IllegalArgumentException("require is required");
    }
}


