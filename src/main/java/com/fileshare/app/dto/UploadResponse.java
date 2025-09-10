package com.fileshare.app.dto;

import java.time.OffsetDateTime;

public record UploadResponse(String viewUrl, OffsetDateTime expiresAt) {}