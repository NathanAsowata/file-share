package com.fileshare.app.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "uploads")
public class Upload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_id", length = 16, nullable = false, unique = true)
    private String shortId;

    @Column(name = "r2_object_key", length = 255, nullable = false, unique = true)
    private String r2ObjectKey;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_type", length = 10, nullable = false)
    private UploadType uploadType;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiresAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShortId() { return shortId; }
    public void setShortId(String shortId) { this.shortId = shortId; }
    public String getR2ObjectKey() { return r2ObjectKey; }
    public void setR2ObjectKey(String r2ObjectKey) { this.r2ObjectKey = r2ObjectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public UploadType getUploadType() { return uploadType; }
    public void setUploadType(UploadType uploadType) { this.uploadType = uploadType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}