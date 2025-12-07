package com.ctrlf.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CreateEducationRequest {
    @NotBlank
    private String title;
    private String description;
    @NotBlank
    private String category;
    @NotNull
    private Boolean require;
    private Integer passScore;
    private Integer passRatio;
    private List<String> departmentScope;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public Boolean getRequire() {
        return require;
    }

    public Integer getPassScore() {
        return passScore;
    }

    public Integer getPassRatio() {
        return passRatio;
    }

    public List<String> getDepartmentScope() {
        return departmentScope;
    }
}


