package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.uploads.LakeUploads;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final LakeUploads uploads;
    private final Access access;

    public UploadController(LakeUploads uploads, Access access) {
        this.uploads = uploads;
        this.access = access;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Uploaded upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        try (InputStream content = file.getInputStream()) {
            return new Uploaded(uploads.store(access.caller().id(), file.getOriginalFilename(), file.getSize(), content));
        }
    }

    public record Uploaded(String key) {}
}
