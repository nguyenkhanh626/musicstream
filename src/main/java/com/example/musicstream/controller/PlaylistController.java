package com.example.musicstream.controller;

import com.example.musicstream.dto.PlaylistDto;
import com.example.musicstream.model.Playlist;
import com.example.musicstream.model.Song;
import com.example.musicstream.security.UserPrincipal;
import com.example.musicstream.service.PlaylistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    // ================= FAVORITES =================

    @PostMapping("/favorites/toggle/{songId}")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @PathVariable Long songId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        boolean isFavorite = playlistService.toggleFavorite(songId, principal.getUser());
        return ResponseEntity.ok(Map.of("songId", songId, "isFavorite", isFavorite));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<Song>> getFavoriteSongs(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.getFavoriteSongs(principal.getUser()));
    }

    @GetMapping("/favorites/ids")
    public ResponseEntity<List<Long>> getFavoriteSongIds(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.getFavoriteSongIds(principal.getUser()));
    }

    // ================= PLAYLISTS =================

    @GetMapping
    public ResponseEntity<List<PlaylistDto>> getUserPlaylists(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.getUserPlaylists(principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<Playlist> createPlaylist(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String name = body.get("name");
        String description = body.get("description");
        Playlist playlist = playlistService.createPlaylist(name, description, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(playlist);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Playlist> getPlaylistDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.getPlaylistDetails(id, principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Playlist> updatePlaylist(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String name = body.get("name");
        String description = body.get("description");
        return ResponseEntity.ok(playlistService.updatePlaylist(id, name, description, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        playlistService.deletePlaylist(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/songs/{songId}")
    public ResponseEntity<Playlist> addSongToPlaylist(
            @PathVariable Long id,
            @PathVariable Long songId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.addSongToPlaylist(id, songId, principal.getUser()));
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public ResponseEntity<Playlist> removeSongFromPlaylist(
            @PathVariable Long id,
            @PathVariable Long songId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(playlistService.removeSongFromPlaylist(id, songId, principal.getUser()));
    }
}

