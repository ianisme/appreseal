package com.appreseal.web;

import com.appreseal.service.ArtifactService;
import com.appreseal.service.DownloadLinks;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 短时签名下载：URL 自带签名和过期时间，不再依赖 Authorization 头。
 * 这样浏览器原生下载器（以及续传请求）可以直接访问，链接泄露也只在几分钟内有效。
 */
@RestController
@RequestMapping("/api/v1/dl")
public class DownloadController {
    private final DownloadLinks links;
    private final ArtifactService artifacts;

    public DownloadController(DownloadLinks links, ArtifactService artifacts) {
        this.links = links;
        this.artifacts = artifacts;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token, HttpServletRequest request) throws IOException {
        DownloadLinks.Grant grant = links.verify(token);
        ArtifactService.FileRef ref = grant.kind() == DownloadLinks.Kind.TASK
                ? artifacts.taskFile(grant.resourceId(), grant.userId(), grant.admin())
                : artifacts.libraryFile(grant.resourceId(), grant.userId(), grant.admin());
        String name = ArtifactService.downloadName(ref.path(), ref.title(), ref.version());
        return DownloadResponses.file(ref.path(), name,
                request.getHeader(HttpHeaders.RANGE),
                request.getHeader(HttpHeaders.IF_RANGE),
                request.getHeader(HttpHeaders.IF_NONE_MATCH));
    }
}
