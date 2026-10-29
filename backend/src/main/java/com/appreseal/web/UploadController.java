package com.appreseal.web;

import com.appreseal.common.ApiException;
import com.appreseal.security.CurrentUser;
import com.appreseal.service.UploadSessionService;
import com.appreseal.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** 大文件分片上传。浏览器按偏移续传，收齐后再建脱壳任务。 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadSessionService sessions;

    public UploadController(UploadSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public Dtos.UploadStatusDto create(@Valid @RequestBody Dtos.CreateUploadRequest req,
                                       @AuthenticationPrincipal CurrentUser me) {
        return sessions.create(require(me).id(), req.filename(), req.size());
    }

    @GetMapping("/{id}")
    public Dtos.UploadStatusDto status(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return sessions.status(require(me).id(), id);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Dtos.UploadStatusDto> append(@PathVariable String id,
                                                       @RequestHeader("X-Upload-Offset") long offset,
                                                       @AuthenticationPrincipal CurrentUser me,
                                                       HttpServletRequest request) throws IOException {
        UploadSessionService.AppendResult result = sessions.append(require(me).id(), id, offset, request.getInputStream());
        if (result.conflict()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result.status());
        }
        return ResponseEntity.ok(result.status());
    }

    @PostMapping("/{id}/complete")
    public Dtos.TaskDto complete(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return sessions.complete(require(me).id(), id);
    }

    private static CurrentUser require(CurrentUser me) {
        if (me == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return me;
    }
}
