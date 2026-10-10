package com.example.musicstream.service;

import com.example.musicstream.model.Role;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.SongRepository;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class SongService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "ogg", "m4a", "aac"
    );

    static {
        // Tắt log debug từ jaudiotagger để giữ console gọn gàng
        try {
            Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        } catch (Exception ignored) {
        }
    }

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public Song uploadSong(MultipartFile file, String inputTitle, String inputArtist) throws Exception {
        return uploadSong(file, inputTitle, inputArtist, null, null);
    }

    public Song uploadSong(MultipartFile file, String inputTitle, String inputArtist, User uploader) throws Exception {
        return uploadSong(file, inputTitle, inputArtist, null, uploader);
    }

    public Song uploadSong(MultipartFile file, String inputTitle, String inputArtist, String inputAlbum, User uploader) throws Exception {
        if (uploader == null) {
            throw new AccessDeniedException("Vui lòng đăng nhập để tải nhạc lên kho cá nhân");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.mp3";
        String ext = "";
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex >= 0) {
            ext = originalName.substring(dotIndex + 1).toLowerCase();
        }

        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Định dạng file không được hỗ trợ (." + ext + "). Hệ thống chỉ hỗ trợ: MP3, WAV, FLAC, OGG, M4A, AAC");
        }

        String format = switch (ext) {
            case "mp3" -> "MP3";
            case "wav" -> "WAV";
            case "flac" -> "FLAC";
            case "ogg" -> "OGG";
            case "m4a" -> "M4A";
            case "aac" -> "AAC";
            default -> ext.toUpperCase();
        };

        String contentType = switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            default -> "audio/" + ext;
        };

        // Tạo thư mục riêng cho từng người dùng (ví dụ: ./storage/audio/user_1)
        Path uploadPath = Paths.get(uploadDir, "user_" + uploader.getId());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String storedName = UUID.randomUUID() + "." + ext;
        Path targetPath = uploadPath.resolve(storedName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Đọc metadata (Title, Artist, Album, Duration, Cover Art) từ file âm thanh bằng jaudiotagger
        String title = inputTitle;
        String artist = inputArtist;
        String album = inputAlbum;
        long duration = 0;
        String coverPath = null;
        boolean hasCover = false;

        try {
            AudioFile audioFile = AudioFileIO.read(targetPath.toFile());
            AudioHeader audioHeader = audioFile.getAudioHeader();
            if (audioHeader != null) {
                duration = (long) audioHeader.getTrackLength();
            }
            Tag tag = audioFile.getTag();
            if (tag != null) {
                String tagTitle = tag.getFirst(FieldKey.TITLE);
                String tagArtist = tag.getFirst(FieldKey.ARTIST);
                String tagAlbum = tag.getFirst(FieldKey.ALBUM);

                if ((title == null || title.isBlank()) && tagTitle != null && !tagTitle.isBlank()) {
                    title = tagTitle.trim();
                }
                if ((artist == null || artist.isBlank()) && tagArtist != null && !tagArtist.isBlank()) {
                    artist = tagArtist.trim();
                }
                if ((album == null || album.isBlank()) && tagAlbum != null && !tagAlbum.isBlank()) {
                    album = tagAlbum.trim();
                }

                // Trích xuất ảnh bìa (Cover / Artwork) nếu có trong file nhạc
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    String imgExt = "jpg";
                    String mime = artwork.getMimeType();
                    if (mime != null && mime.toLowerCase().contains("png")) {
                        imgExt = "png";
                    }
                    String coverFileName = "cover_" + UUID.randomUUID() + "." + imgExt;
                    Path coverFilePath = uploadPath.resolve(coverFileName);
                    Files.write(coverFilePath, artwork.getBinaryData());
                    coverPath = coverFilePath.toString();
                    hasCover = true;
                }
            }
        } catch (Exception ignored) {
            // Trường hợp file không có metadata chuẩn, giữ nguyên fallback
        }

        if (title == null || title.isBlank()) {
            title = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
        }
        if (artist == null || artist.isBlank()) {
            artist = "Unknown Artist";
        }
        if (album == null || album.isBlank()) {
            album = "Single";
        }

        Song song = Song.builder()
                .title(title)
                .artist(artist)
                .album(album)
                .durationInSeconds(duration)
                .originalFileName(originalName)
                .storageFileName(storedName)
                .filePath(targetPath.toString())
                .fileSize(file.getSize())
                .format(format)
                .contentType(contentType)
                .coverPath(coverPath)
                .hasCover(hasCover)
                .uploadedAt(LocalDateTime.now())
                .uploader(uploader)
                .build();

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

    public Song getSongByIdForUser(Long id, User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để truy cập bài hát");
        }

        Song song = getSongById(id);
        boolean isOwner = song.getUploader() != null && song.getUploader().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền truy cập bài hát trong kho của người khác");
        }

        return song;
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
            if (song.getCoverPath() != null) {
                Files.deleteIfExists(Paths.get(song.getCoverPath()));
            }
        } catch (IOException ignored) {
        }

        songRepository.delete(song);
    }
}