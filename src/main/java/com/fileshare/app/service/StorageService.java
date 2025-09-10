package com.fileshare.app.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import com.fileshare.app.dto.MetadataResponse;
import com.fileshare.app.dto.UploadResponse;
import com.fileshare.app.model.Upload;
import com.fileshare.app.model.UploadType;
import com.fileshare.app.repository.UploadRepository;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class StorageService {

    private final AmazonS3 s3Client;
    private final UploadRepository uploadRepository;
    private final String bucketName;
    private final String appDomain;
    private final List<String> allowedExtensions;
    private static final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final PolicyFactory htmlSanitizerPolicy = Sanitizers.FORMATTING.and(Sanitizers.BLOCKS);


    public StorageService(AmazonS3 s3Client, UploadRepository uploadRepository,
                          @Value("${aws.s3.bucket-name}") String bucketName,
                          @Value("${app.domain}") String appDomain,
                          @Value("${app.file.allowed-extensions}") String[] allowedExtensions) {
        this.s3Client = s3Client;
        this.uploadRepository = uploadRepository;
        this.bucketName = bucketName;
        this.appDomain = appDomain;
        this.allowedExtensions = Arrays.asList(allowedExtensions);
    }

    @Transactional
    public UploadResponse upload(MultipartFile file, String text) throws IOException {
        if ((file == null || file.isEmpty()) && (text == null || text.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either a file or text content must be provided.");
        }
        if (file != null && !file.isEmpty() && text != null && !text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload both a file and text content simultaneously.");
        }

        Upload upload = new Upload();
        byte[] contentBytes;
        String originalFilename;
        String contentType;

        if (text != null && !text.isBlank()) {
            if (text.length() > 100000) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Text content must not exceed 100,000 characters.");
            }
            String sanitizedText = htmlSanitizerPolicy.sanitize(text);
            contentBytes = sanitizedText.getBytes();
            originalFilename = "Text Snippet";
            contentType = "text/plain";
            upload.setUploadType(UploadType.TEXT);
        } else {
            validateFile(file);
            contentBytes = file.getBytes();
            originalFilename = file.getOriginalFilename();
            contentType = file.getContentType();
            upload.setUploadType(UploadType.FILE);
        }

        String shortId = generateShortId();
        String r2ObjectKey = UUID.randomUUID().toString();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        metadata.setContentType(contentType);

        try (InputStream inputStream = new ByteArrayInputStream(contentBytes)) {
            s3Client.putObject(bucketName, r2ObjectKey, inputStream, metadata);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
        upload.setShortId(shortId);
        upload.setR2ObjectKey(r2ObjectKey);
        upload.setOriginalFilename(originalFilename);
        upload.setContentType(contentType);
        upload.setCreatedAt(now);
        upload.setExpiresAt(now.plusHours(24));

        uploadRepository.save(upload);

        String viewUrl = appDomain + "/view/" + shortId;
        return new UploadResponse(viewUrl, upload.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public MetadataResponse getMetadata(String shortId) throws IOException {
        Upload upload = uploadRepository.findByShortId(shortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or has expired."));

        String textContent = null;
        if (upload.getUploadType() == UploadType.TEXT || "text/markdown".equalsIgnoreCase(upload.getContentType())) {
            S3Object s3Object = s3Client.getObject(bucketName, upload.getR2ObjectKey());
            try (S3ObjectInputStream inputStream = s3Object.getObjectContent()) {
                textContent = IOUtils.toString(inputStream);
            }
        }

        return new MetadataResponse(
                upload.getShortId(),
                upload.getOriginalFilename(),
                upload.getUploadType(),
                textContent,
                upload.getExpiresAt()
        );
    }

    public byte[] downloadFile(String shortId, S3ObjectWrapper s3ObjectWrapper) throws IOException {
        Upload upload = uploadRepository.findByShortId(shortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found or has expired."));

        S3Object s3Object = s3Client.getObject(bucketName, upload.getR2ObjectKey());
        s3ObjectWrapper.setS3Object(s3Object);
        return IOUtils.toByteArray(s3Object.getObjectContent());
    }

    // S3ObjectWrapper is a workaround to return both the byte array
    // and the original S3Object from the download method,
    // which is used in the controller to get content type and other related metadata
    public static class S3ObjectWrapper {
        private S3Object s3Object;
        public S3Object getS3Object() { return s3Object; }
        public void setS3Object(S3Object s3Object) { this.s3Object = s3Object; }
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > 25 * 1024 * 1024) { // 25 MB
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File size must not exceed 25 MB.");
        }
        String extension = getFileExtension(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type ." + extension + " is not allowed.");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String generateShortId() {
        byte[] buffer = new byte[6]; // 6 bytes = 8 characters in Base64Url
        random.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }
}