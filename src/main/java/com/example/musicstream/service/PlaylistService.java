package com.example.musicstream.service;

import com.example.musicstream.dto.PlaylistDto;
import com.example.musicstream.model.FavoriteSong;
import com.example.musicstream.model.Playlist;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.FavoriteSongRepository;
import com.example.musicstream.repository.PlaylistRepository;
import com.example.musicstream.repository.SongRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final FavoriteSongRepository favoriteSongRepository;
    private final SongRepository songRepository;
    private final SongService songService;

    public PlaylistService(PlaylistRepository playlistRepository,
                           FavoriteSongRepository favoriteSongRepository,
                           SongRepository songRepository,
                           SongService songService) {
        this.playlistRepository = playlistRepository;
        this.favoriteSongRepository = favoriteSongRepository;
        this.songRepository = songRepository;
        this.songService = songService;
    }

    // ================= FAVORITES =================

    public boolean toggleFavorite(Long songId, User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        // Kiểm tra quyền truy cập bài hát
        Song song = songService.getSongByIdForUser(songId, user);

        var existing = favoriteSongRepository.findByUserIdAndSongId(user.getId(), song.getId());
        if (existing.isPresent()) {
            favoriteSongRepository.delete(existing.get());
            return false; // Đã bỏ thích
        } else {
            FavoriteSong favorite = FavoriteSong.builder()
                    .user(user)
                    .song(song)
                    .createdAt(LocalDateTime.now())
                    .build();
            favoriteSongRepository.save(favorite);
            return true; // Đã thêm vào thích
        }
    }

    public List<Song> getFavoriteSongs(User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        return favoriteSongRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(FavoriteSong::getSong)
                .collect(Collectors.toList());
    }

    public List<Long> getFavoriteSongIds(User user) {
        if (user == null) return List.of();
        return favoriteSongRepository.findFavoriteSongIdsByUserId(user.getId());
    }

    // ================= PLAYLISTS =================

    public List<PlaylistDto> getUserPlaylists(User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        return playlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(p -> PlaylistDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .songCount(p.getSongs().size())
                        .createdAt(p.getCreatedAt())
                        .updatedAt(p.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public Playlist createPlaylist(String name, String description, User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên playlist không được để trống");
        }

        Playlist playlist = Playlist.builder()
                .name(name.trim())
                .description(description != null ? description.trim() : "")
                .user(user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return playlistRepository.save(playlist);
    }

    public Playlist getPlaylistDetails(Long playlistId, User user) {
        if (user == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        Playlist playlist = playlistRepository.findByIdAndUserId(playlistId, user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy playlist với id: " + playlistId));
        // Trigger lazy load
        playlist.getSongs().size();
        return playlist;
    }

    public Playlist updatePlaylist(Long playlistId, String name, String description, User user) {
        Playlist playlist = getPlaylistDetails(playlistId, user);
        if (name != null && !name.isBlank()) {
            playlist.setName(name.trim());
        }
        if (description != null) {
            playlist.setDescription(description.trim());
        }
        playlist.setUpdatedAt(LocalDateTime.now());
        return playlistRepository.save(playlist);
    }

    public void deletePlaylist(Long playlistId, User user) {
        Playlist playlist = getPlaylistDetails(playlistId, user);
        playlistRepository.delete(playlist);
    }

    public Playlist addSongToPlaylist(Long playlistId, Long songId, User user) {
        Playlist playlist = getPlaylistDetails(playlistId, user);
        Song song = songService.getSongByIdForUser(songId, user);

        boolean exists = playlist.getSongs().stream().anyMatch(s -> s.getId().equals(song.getId()));
        if (!exists) {
            playlist.getSongs().add(song);
            playlist.setUpdatedAt(LocalDateTime.now());
            return playlistRepository.save(playlist);
        }
        return playlist;
    }

    public Playlist removeSongFromPlaylist(Long playlistId, Long songId, User user) {
        Playlist playlist = getPlaylistDetails(playlistId, user);
        playlist.getSongs().removeIf(s -> s.getId().equals(songId));
        playlist.setUpdatedAt(LocalDateTime.now());
        return playlistRepository.save(playlist);
    }
}

