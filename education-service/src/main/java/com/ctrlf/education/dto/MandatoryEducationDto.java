package com.ctrlf.education.dto;

import java.util.UUID;

public class MandatoryEducationDto {
    private UUID id;
    private String title;
    private boolean isCompleted;
    private boolean required;

    public MandatoryEducationDto(UUID id, String title, boolean isCompleted, boolean required) {
        this.id = id;
        this.title = title;
        this.isCompleted = isCompleted;
        this.required = required;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public boolean isRequired() {
        return required;
    }
}


