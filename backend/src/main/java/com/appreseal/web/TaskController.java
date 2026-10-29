package com.appreseal.web;

import com.appreseal.common.ApiException;
import com.appreseal.security.CurrentUser;
import com.appreseal.service.ArtifactService;
import com.appreseal.service.DownloadLinks;
import com.appreseal.service.TaskService;
import com.appreseal.service.UploadService;
import com.appreseal.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService tasks;
    private final ArtifactService artifacts;
    private final UploadService uploads;
    private final DownloadLinks links;

    public TaskController(TaskService tasks, ArtifactService artifacts, UploadService uploads, DownloadLinks links) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.uploads = uploads;
        this.links = links;
    }

    @GetMapping
    public Dtos.TaskPageDto<Dtos.TaskDto> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @AuthenticationPrincipal CurrentUser me) {
        return tasks.list(require(me).id(), status, page, size);
    }

    @PostMapping
    public Dtos.TaskDto create(@Valid @RequestBody Dtos.CreateTaskRequest req,
                               @AuthenticationPrincipal CurrentUser me) {
        return tasks.create(require(me).id(), req);
    }

    /** 上传加密 IPA 并直接建单：服务端读取 Info.plist 与 cryptid，校验通过才扣费。 */
    @PostMapping("/upload")
    public Dtos.TaskDto upload(@RequestPart("file") MultipartFile file,
                               @AuthenticationPrincipal CurrentUser me) {
        return uploads.create(require(me).id(), file);
    }

    @GetMapping("/{id}")
    public Dtos.TaskDto get(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        CurrentUser user = require(me);
        return tasks.get(id, user.id(), user.isAdmin());
    }

    @PostMapping("/{id}/cancel")
    public Dtos.TaskDto cancel(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        CurrentUser user = require(me);
        return tasks.cancel(id, user.id(), user.isAdmin());
    }

    @PostMapping("/{id}/retry")
    public Dtos.TaskDto retry(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        CurrentUser user = require(me);
        return tasks.retry(id, user.id(), user.isAdmin());
    }

    /** 生成短时下载链接；先校验一次权限和产物可用性，失败时直接返回业务错误。 */
    @PostMapping("/{id}/download-link")
    public Dtos.DownloadLinkDto downloadLink(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        CurrentUser user = require(me);
        artifacts.taskFile(id, user.id(), user.isAdmin());
        DownloadLinks.Issued issued = links.issue(DownloadLinks.Kind.TASK, id, user.id(), user.isAdmin());
        return new Dtos.DownloadLinkDto("/api/v1/dl/" + issued.token(), issued.expiresAt());
    }

    /** 兼容旧链接：仍然需要登录态，但同样支持 Range。 */
    @GetMapping("/{id}/artifact")
    public ResponseEntity<Resource> artifact(@PathVariable String id, @AuthenticationPrincipal CurrentUser me,
                                      HttpServletRequest request) throws IOException {
        CurrentUser user = require(me);
        var ref = artifacts.taskFile(id, user.id(), user.isAdmin());
        String name = ArtifactService.downloadName(ref.path(), ref.title(), ref.version());
        return DownloadResponses.file(ref.path(), name,
                request.getHeader(HttpHeaders.RANGE),
                request.getHeader(HttpHeaders.IF_RANGE),
                request.getHeader(HttpHeaders.IF_NONE_MATCH));
    }

    private static CurrentUser require(CurrentUser me) {
        if (me == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return me;
    }
}
