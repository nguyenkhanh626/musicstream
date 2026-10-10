package com.example.musicstream.controller;

import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.SongRepository;
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
    private final SongRepository songRepository;

    public SongController(SongService songService, SongRepository songRepository) {
        this.songService = songService;
        this.songRepository = songRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<Song> uploadSong(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "artist", required = false) String artist,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws Exception {
        User uploader = principal != null ? principal.getUser() : null;
        Song savedSong = songService.uploadSong(file, title, artist, uploader);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedSong);
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<ResourceRegion> streamSong(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers
    ) throws IOException {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát"));

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
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaTypeFactory.getMediaType(audioResource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(region);
        } else {
            long rangeLength = Math.min(chunkSize, contentLength);
            region = new ResourceRegion(audioResource, 0, rangeLength);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaTypeFactory.getMediaType(audioResource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .body(region);
        }
    }

    @GetMapping
    public ResponseEntity<List<Song>> getAllSongs() {
        return ResponseEntity.ok(songService.getAllSongs());
    }

    @GetMapping("/my-songs")
    public ResponseEntity<List<Song>> getMySongs(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(songService.getSongsByUploader(principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Song> getSongById(@PathVariable Long id) {
        return ResponseEntity.ok(songService.getSongById(id));
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