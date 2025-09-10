package com.fileshare.app.service;

import com.amazonaws.services.s3.AmazonS3;
import com.fileshare.app.model.Upload;
import com.fileshare.app.repository.UploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@EnableScheduling
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);
    private final UploadRepository uploadRepository;
    private final AmazonS3 s3Client;
    private final String bucketName;

    public CleanupService(UploadRepository uploadRepository, AmazonS3 s3Client, @Value("${aws.s3.bucket-name}") String bucketName) {
        this.uploadRepository = uploadRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Transactional
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupExpiredFiles() {
        log.info("Starting cleanup task for expired files...");
        List<Upload> expiredUploads = uploadRepository.findByExpiresAtBefore(OffsetDateTime.now(ZoneId.of("UTC")));

        if (expiredUploads.isEmpty()) {
            log.info("No expired files to clean up.");
            return;
        }

        log.info("Found {} expired files to delete.", expiredUploads.size());

        for (Upload upload : expiredUploads) {
            try {
                log.info("Deleting object {} from R2 bucket.", upload.getR2ObjectKey());
                s3Client.deleteObject(bucketName, upload.getR2ObjectKey());
                uploadRepository.delete(upload);
                log.info("Successfully deleted R2 object and database record for shortId {}.", upload.getShortId());
            } catch (Exception e) {
                log.error("Error during cleanup for shortId {}: {}", upload.getShortId(), e.getMessage());
                // Even if one fails move on to the next file
            }
        }
        log.info("Cleanup task finished.");
    }
}