package com.example.musicstream.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/api/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "RUNNING",
                "message", "Hệ thống Music Streaming Backend đang hoạt động!",
                "h2_console", "http://localhost:8080/h2-console/",
                "apis", Map.of(
                        "register", "POST /api/auth/register",
                        "login", "POST /api/auth/login",
                        "profile", "GET /api/auth/me (Cần Bearer Token)",
                        "get_all_songs", "GET /api/songs",
                        "my_songs", "GET /api/songs/my-songs (Cần Bearer Token)",
                        "upload_song", "POST /api/songs/upload (Cần Bearer Token)",
                        "stream_song", "GET /api/songs/stream/{id}"
                )
        ));
    }

    @GetMapping("/h2-console")
    public void redirectToH2Console(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/h2-console/");
    }
}

