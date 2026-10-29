package com.appreseal.web;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * 产物下载响应：支持 {@code Range} 断点续传、{@code If-Range} 与 {@code ETag} 校验。
 *
 * <p>浏览器/下载器可以拿到 206 partial content，弱网中断后从断点继续，而不是整包重下。
 * 只读产物目录内的文件（路径由 {@code ArtifactService} 解析并校验）。
 *
 * <p>这里不返回 {@code ResourceRegion}：控制器声明的是 {@code ResponseEntity<Resource>}，
 * Spring 的转换器会按声明类型选择，用切片 {@link Resource} 更稳，也能直接落到 200/206 两种响应。
 */
public final class DownloadResponses {
    private DownloadResponses() {}

    public static ResponseEntity<Resource> file(Path path, String filename, String rangeHeader,
                                                String ifRange, String ifNoneMatch) throws IOException {
        long size = Files.size(path);
        String tag = etag(path, size);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        MediaType type = filename.endsWith(".ipa") ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;

        if (matches(ifNoneMatch, tag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(tag).build();
        }

        boolean rangeAllowed = ifRange == null || ifRange.isBlank() || ifRange.contains(tag);
        if (size <= 0 || !rangeAllowed || rangeHeader == null || rangeHeader.isBlank()) {
            return base(HttpStatus.OK, disposition, tag, type)
                    .contentLength(size)
                    .body(new FileSystemResource(path));
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            // 语法不合法的 Range 按 RFC 忽略，返回整包。
            return base(HttpStatus.OK, disposition, tag, type)
                    .contentLength(size)
                    .body(new FileSystemResource(path));
        }
        if (ranges.size() != 1) {
            // 多段 Range 直接退回整包，避免拼 multipart/byteranges。
            return base(HttpStatus.OK, disposition, tag, type)
                    .contentLength(size)
                    .body(new FileSystemResource(path));
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(size);
        long end = range.getRangeEnd(size);
        if (start > end || start >= size) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                    .eTag(tag)
                    .build();
        }
        long count = end - start + 1;
        return base(HttpStatus.PARTIAL_CONTENT, disposition, tag, type)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size)
                .contentLength(count)
                .body(new RegionResource(path, start, count));
    }

    public static String etag(Path path, long size) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return "\"" + Long.toHexString(size) + "-" + Long.toHexString(modified.toMillis()) + "\"";
        } catch (IOException e) {
            return "\"" + Long.toHexString(size) + "\"";
        }
    }

    private static ResponseEntity.BodyBuilder base(HttpStatus status, ContentDisposition disposition,
                                                   String tag, MediaType type) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .eTag(tag)
                .contentType(type);
    }

    private static boolean matches(String ifNoneMatch, String tag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String part : ifNoneMatch.split(",")) {
            String value = part.trim();
            if ("*".equals(value) || value.equals(tag) || ("W/" + value).equals(tag) || value.equals("W/" + tag)) {
                return true;
            }
        }
        return false;
    }

    /** 文件的一段：[start, start+count)。流式读取，不会把整个 IPA 读进内存。 */
    static final class RegionResource extends AbstractResource {
        private final Path path;
        private final long start;
        private final long count;

        RegionResource(Path path, long start, long count) {
            this.path = path;
            this.start = start;
            this.count = count;
        }

        @Override
        public String getDescription() {
            return "region " + start + "+" + count + " of " + path.getFileName();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            InputStream in = Files.newInputStream(path);
            try {
                in.skipNBytes(start);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
            return new LimitedInputStream(in, count);
        }

        @Override
        public long contentLength() {
            return count;
        }

        @Override
        public String getFilename() {
            return path.getFileName().toString();
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
