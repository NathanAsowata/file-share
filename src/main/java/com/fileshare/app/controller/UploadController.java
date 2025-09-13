package com.fileshare.app.controller;

import com.fileshare.app.dto.MetadataResponse;
import com.fileshare.app.dto.UploadResponse;
import com.fileshare.app.service.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = {"https://share.nathanasowata.com", "http://localhost:5173"})
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadContent(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text) throws IOException {
        UploadResponse response = storageService.upload(file, text);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/meta/{shortId}")
    public ResponseEntity<MetadataResponse> getMetadata(@PathVariable String shortId) throws IOException {
        MetadataResponse metadata = storageService.getMetadata(shortId);
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/download/{shortId}")
    public ResponseEntity<byte[]> downloadContent(@PathVariable String shortId) throws IOException {
        StorageService.S3ObjectWrapper s3ObjectWrapper = new StorageService.S3ObjectWrapper();
        byte[] data = storageService.downloadFile(shortId, s3ObjectWrapper);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(s3ObjectWrapper.getS3Object().getObjectMetadata().getContentType()));
        headers.setContentLength(s3ObjectWrapper.getS3Object().getObjectMetadata().getContentLength());
        headers.setContentDispositionFormData("attachment", s3ObjectWrapper.getS3Object().getObjectMetadata().getUserMetaDataOf("original-filename"));

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}