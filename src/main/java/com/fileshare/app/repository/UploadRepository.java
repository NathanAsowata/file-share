package com.fileshare.app.repository;

import com.fileshare.app.model.Upload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadRepository extends JpaRepository<Upload, Long> {
    Optional<Upload> findByShortId(String shortId);
    List<Upload> findByExpiresAtBefore(OffsetDateTime now);
}