package com.fileshare.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.time.OffsetDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", ex.getStatusCode().value(),
                "error", HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase(),
                "message", ex.getReason()
        );
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "error", HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                "message", "File size exceeds the limit of 25MB."
        );
        return new ResponseEntity<>(body, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "message", "An unexpected internal server error occurred."
        );
        // Log the exception details for debugging
        logger.error("An unexpected error occurred: ", ex);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}