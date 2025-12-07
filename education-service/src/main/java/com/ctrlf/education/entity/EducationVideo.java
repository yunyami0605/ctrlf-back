package com.ctrlf.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "education_video", schema = "education")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "education_id", columnDefinition = "uuid")
    private UUID educationId;

    @Column(name = "generation_job_id", columnDefinition = "uuid")
    private UUID generationJobId;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "version")
    private Integer version;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "is_main")
    private Boolean isMain;

    @Column(name = "status")
    private String status;

    @Column(name = "target_dept_code")
    private String targetDeptCode;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}


