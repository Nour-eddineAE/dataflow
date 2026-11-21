package com.dataflow.workspace.web;

import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.catalog.UnknownTableException;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.folders.UnknownFolderException;
import com.dataflow.workspace.runs.TowerException;
import com.dataflow.workspace.serving.ServingQueryException;
import com.dataflow.workspace.serving.UnknownFunctionException;
import com.dataflow.workspace.specs.InvalidDraftException;
import com.dataflow.workspace.specs.Problem;
import com.dataflow.workspace.specs.UnknownSpecException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiErrors {

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, List<Problem> problems) {
        return ResponseEntity.status(status)
                .body(new ApiError(message == null ? status.getReasonPhrase() : message, problems));
    }

    @ExceptionHandler({UnknownSpecException.class, UnknownTableException.class, UnknownFunctionException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(InvalidDraftException.class)
    public ResponseEntity<ApiError> invalid(InvalidDraftException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e.problems());
    }

    @ExceptionHandler({IllegalArgumentException.class, ServingQueryException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than this install accepts", null);
    }

    @ExceptionHandler(TowerException.class)
    public ResponseEntity<ApiError> tower(TowerException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    @ExceptionHandler(UnknownFolderException.class)
    public ResponseEntity<ApiError> unknownFolder(UnknownFolderException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(GitFolderException.class)
    public ResponseEntity<ApiError> git(GitFolderException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), e.problems().isEmpty() ? null : e.problems());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> unexpected(RuntimeException e) {
        log.error("Request failed", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), null);
    }

    public record ApiError(String error, List<Problem> problems) {}
}
