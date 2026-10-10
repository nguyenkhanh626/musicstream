package com.example.musicstream.service;

import com.example.musicstream.model.Role;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.SongRepository;
import com.mpatric.mp3agic.Mp3File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
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
        return uploadSong(file, inputTitle, inputArtist, null);
    }

    public Song uploadSong(MultipartFile file, String inputTitle, String inputArtist, User uploader) throws Exception {
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
                .uploader(uploader)
                .build();

        // Gán uploader bằng setter để đảm bảo tương thích kể cả khi Lombok cache chưa cập nhật builder trong IDE
        if (uploader != null) {
            song.setUploader(uploader);
        }

        return songRepository.save(song);
    }

    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    public List<Song> getSongsByUploader(Long userId) {
        return songRepository.findByUploaderId(userId);
    }

    public Song getSongById(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài hát với id: " + id));
    }

    public void deleteSong(Long id, User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện thao tác này");
        }

        Song song = getSongById(id);
        boolean isOwner = song.getUploader() != null && song.getUploader().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền xóa bài hát này");
        }

        // Xóa file vật lý trên ổ đĩa
        try {
            if (song.getFilePath() != null) {
                Files.deleteIfExists(Paths.get(song.getFilePath()));
            }
        } catch (IOException ignored) {
        }

        songRepository.delete(song);
    }
}