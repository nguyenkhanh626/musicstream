package com.example.musicstream.service;

import com.example.musicstream.model.Song;
import com.example.musicstream.repository.SongRepository;
import com.mpatric.mp3agic.Mp3File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SongService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public Song uploadSong(MultipartFile file, String inputTitle, String inputArtist) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".") 
                ? originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        String storedName = UUID.randomUUID() + extension;
        Path targetPath = uploadPath.resolve(storedName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Đọc metadata ID3 từ file mp3 nếu người dùng không nhập tay
        String title = inputTitle;
        String artist = inputArtist;
        long duration = 0;

        try {
            Mp3File mp3file = new Mp3File(targetPath.toFile());
            duration = mp3file.getLengthInSeconds();
            if (mp3file.hasId3v2Tag()) {
                var id3v2 = mp3file.getId3v2Tag();
                if (title == null || title.isBlank()) title = id3v2.getTitle();
                if (artist == null || artist.isBlank()) artist = id3v2.getArtist();
            }
        } catch (Exception ignored) {
            // Trường hợp file không chuẩn ID3, giữ nguyên fallback
        }

        if (title == null || title.isBlank()) title = originalName;
        if (artist == null || artist.isBlank()) artist = "Unknown Artist";

        Song song = Song.builder()
                .title(title)
                .artist(artist)
                .durationInSeconds(duration)
                .originalFileName(originalName)
                .storageFileName(storedName)
                .filePath(targetPath.toString())
                .fileSize(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .build();

        return songRepository.save(song);
    }
}