package com.fileshare.app.dto;

import com.fileshare.app.model.UploadType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataResponse(
        String shortId,
        String originalFilename,
        UploadType uploadType,
        String textContent,
        OffsetDateTime expiresAt
) {}