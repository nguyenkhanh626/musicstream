package com.example.musicstream.controller;

import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.security.UserPrincipal;
import com.example.musicstream.service.SongService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Song> uploadSong(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "artist", required = false) String artist,
            @RequestParam(value = "album", required = false) String album,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws Exception {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Song savedSong = songService.uploadSong(file, title, artist, album, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSong);
    }

    @GetMapping("/cover/{id}")
    public ResponseEntity<Resource> getSongCover(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Song song = songService.getSongByIdForUser(id, principal.getUser());
        if (song.getCoverPath() != null && java.nio.file.Files.exists(java.nio.file.Paths.get(song.getCoverPath()))) {
            Resource resource = new FileSystemResource(song.getCoverPath());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.IMAGE_JPEG);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS))
                    .contentType(mediaType)
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<ResourceRegion> streamSong(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Kiểm tra quyền sở hữu bài hát trong kho riêng
        Song song = songService.getSongByIdForUser(id, principal.getUser());

        Resource audioResource = new FileSystemResource(song.getFilePath());
        long contentLength = audioResource.contentLength();
        
        // Chunk size mỗi lần tải: khoảng 1MB
        long chunkSize = 1024 * 1024L; 
        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);
        
        ResourceRegion region;
        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(chunkSize, end - start + 1);
            region = new ResourceRegion(audioResource, start, rangeLength);
        } else {
            long rangeLength = Math.min(chunkSize, contentLength);
            region = new ResourceRegion(audioResource, 0, rangeLength);
        }

        MediaType mediaType;
        if (song.getContentType() != null && !song.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(song.getContentType());
            } catch (Exception e) {
                mediaType = MediaTypeFactory.getMediaType(audioResource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            }
        } else {
            mediaType = MediaTypeFactory.getMediaType(audioResource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .body(region);
    }

    @GetMapping
    public ResponseEntity<List<Song>> getMySongs(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Kho nhạc riêng biệt: Chỉ trả về danh sách bài hát của chính người dùng này
        return ResponseEntity.ok(songService.getSongsByUploader(principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Song> getSongById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(songService.getSongByIdForUser(id, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSong(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        songService.deleteSong(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}