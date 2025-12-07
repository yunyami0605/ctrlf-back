package com.ctrlf.education.dto;

import java.util.UUID;

public class CreateEducationResponse {
    private UUID eduId;
    private String status;

    public CreateEducationResponse(UUID eduId, String status) {
        this.eduId = eduId;
        this.status = status;
    }

    public UUID getEduId() {
        return eduId;
    }

    public String getStatus() {
        return status;
    }
}


