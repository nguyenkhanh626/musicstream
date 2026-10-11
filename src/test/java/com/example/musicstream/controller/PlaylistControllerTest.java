package com.example.musicstream.controller;

import com.example.musicstream.model.Role;
import com.example.musicstream.model.Song;
import com.example.musicstream.model.User;
import com.example.musicstream.repository.FavoriteSongRepository;
import com.example.musicstream.repository.PlaylistRepository;
import com.example.musicstream.repository.SongRepository;
import com.example.musicstream.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PlaylistControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private FavoriteSongRepository favoriteSongRepository;

    private MockMvc mockMvc;
    private User testUser;
    private String token;
    private Song testSong;

    @BeforeEach
    void setUp() throws Exception {
        playlistRepository.deleteAll();
        favoriteSongRepository.deleteAll();
        songRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String jsonBody = """
            {
                "username": "playlist_fan",
                "email": "playlist@example.com",
                "password": "secret123",
                "displayName": "Playlist Fan"
            }
        """;

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andReturn();

        token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        testUser = userRepository.findByUsername("playlist_fan").orElseThrow();

        testSong = Song.builder()
                .title("Chill Sunset")
                .artist("DJ Sunset")
                .album("Summer Vibes")
                .durationInSeconds(180L)
                .originalFileName("sunset.mp3")
                .storageFileName("sunset-uuid.mp3")
                .filePath("./target/test-storage/sunset.mp3")
                .format("MP3")
                .contentType("audio/mpeg")
                .hasCover(false)
                .uploadedAt(LocalDateTime.now())
                .uploader(testUser)
                .build();
        testSong = songRepository.save(testSong);
    }

    @Test
    @DisplayName("Toggle favorite song: add and remove successfully")
    void testToggleFavoriteSong() throws Exception {
        // Lần 1: Thêm vào favorite
        mockMvc.perform(post("/api/playlists/favorites/toggle/" + testSong.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(true));

        // Lấy danh sách favorites
        mockMvc.perform(get("/api/playlists/favorites")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Chill Sunset"));

        // Lần 2: Bấm lại -> xóa khỏi favorite
        mockMvc.perform(post("/api/playlists/favorites/toggle/" + testSong.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(false));

        // Kiểm tra danh sách rỗng
        mockMvc.perform(get("/api/playlists/favorites")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Create playlist, add song, and remove song")
    void testPlaylistLifecycle() throws Exception {
        // Tạo playlist
        String createJson = """
            {
                "name": "Nhạc Buổi Sáng",
                "description": "Playlist để thức dậy mỗi sáng"
            }
        """;

        String res = mockMvc.perform(post("/api/playlists")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nhạc Buổi Sáng"))
                .andReturn().getResponse().getContentAsString();

        // Parse id
        Number playlistIdNum = JsonPath.read(res, "$.id");
        long playlistId = playlistIdNum.longValue();

        // Thêm bài hát vào playlist
        mockMvc.perform(post("/api/playlists/" + playlistId + "/songs/" + testSong.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs", hasSize(1)))
                .andExpect(jsonPath("$.songs[0].title").value("Chill Sunset"));

        // Lấy danh sách playlist của người dùng
        mockMvc.perform(get("/api/playlists")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Nhạc Buổi Sáng"))
                .andExpect(jsonPath("$[0].songCount").value(1));

        // Xóa bài hát khỏi playlist
        mockMvc.perform(delete("/api/playlists/" + playlistId + "/songs/" + testSong.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs", hasSize(0)));
    }
}

