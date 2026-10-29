package com.appreseal.web;

import com.appreseal.common.ApiException;
import com.appreseal.security.CurrentUser;
import com.appreseal.service.ArtifactService;
import com.appreseal.service.CatalogService;
import com.appreseal.service.DownloadLinks;
import com.appreseal.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    private final ArtifactService artifacts;
    private final DownloadLinks links;

    public CatalogController(CatalogService catalog, ArtifactService artifacts, DownloadLinks links) {
        this.catalog = catalog;
        this.artifacts = artifacts;
        this.links = links;
    }

    /** 市场列表：公开接口，支持搜索/分类/排序（updated|downloads|price）。 */
    @GetMapping("/apps")
    public List<Dtos.AppDto> apps(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String sort) {
        return catalog.list(q, category, sort);
    }

    /** 应用详情：公开接口。 */
    @GetMapping("/apps/{bundleId}")
    public Dtos.AppDto app(@PathVariable String bundleId) {
        return catalog.get(bundleId);
    }

    /** 新建脱壳可选市场：只返回 Apple ID 池里已开放的市场。 */
    @GetMapping("/store/markets")
    public List<Dtos.StoreMarket> markets(@AuthenticationPrincipal CurrentUser me) {
        require(me);
        return catalog.openMarkets();
    }

    /** 商店搜索（提交任务时的联想）：需要登录。 */
    @GetMapping("/store/search")
    public Dtos.StoreSearchResult search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String country,
                                         @AuthenticationPrincipal CurrentUser me) {
        return catalog.search(q, country, require(me).id());
    }

    /** 把成品记入当前成员的应用库，不扣费。 */
    @PostMapping("/apps/{bundleId}/download")
    public Dtos.DownloadResult download(@PathVariable String bundleId,
                                        @AuthenticationPrincipal CurrentUser me) {
        return catalog.download(bundleId, require(me).id());
    }

    @GetMapping("/library")
    public List<Dtos.LibraryDto> library(@AuthenticationPrincipal CurrentUser me) {
        return catalog.library(require(me).id());
    }

    @PostMapping("/library/{id}/download-link")
    public Dtos.DownloadLinkDto libraryDownloadLink(@PathVariable String id,
                                                    @AuthenticationPrincipal CurrentUser me) {
        CurrentUser user = require(me);
        artifacts.libraryFile(id, user.id(), user.isAdmin());
        DownloadLinks.Issued issued = links.issue(DownloadLinks.Kind.LIBRARY, id, user.id(), user.isAdmin());
        return new Dtos.DownloadLinkDto("/api/v1/dl/" + issued.token(), issued.expiresAt());
    }

    /** 兼容旧链接：仍然需要登录态，但同样支持 Range。 */
    @GetMapping("/library/{id}/artifact")
    public ResponseEntity<Resource> libraryArtifact(@PathVariable String id, @AuthenticationPrincipal CurrentUser me,
                                             HttpServletRequest request) throws IOException {
        CurrentUser user = require(me);
        var file = artifacts.libraryFile(id, user.id(), user.isAdmin());
        String name = ArtifactService.downloadName(file.path(), file.title(), file.version());
        return DownloadResponses.file(file.path(), name,
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
