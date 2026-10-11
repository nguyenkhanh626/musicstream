package com.example.musicstream.controller;

import com.example.musicstream.repository.SongRepository;
import com.example.musicstream.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SongMultiFormatTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private com.example.musicstream.repository.FavoriteSongRepository favoriteSongRepository;

    @Autowired
    private com.example.musicstream.repository.PlaylistRepository playlistRepository;

    private MockMvc mockMvc;
    private String token;

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

        // Đăng ký và lấy JWT token
        String jsonBody = """
            {
                "username": "audiotester",
                "email": "audiotester@example.com",
                "password": "secret123",
                "displayName": "Audio Tester"
            }
        """;

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andReturn();

        token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void testUploadAndStreamDifferentFormats() throws Exception {
        String[] formats = {"mp3", "wav", "flac", "ogg", "m4a", "aac"};
        String[] expectedContentTypes = {
                "audio/mpeg", "audio/wav", "audio/flac", "audio/ogg", "audio/mp4", "audio/aac"
        };
        String[] expectedFormatNames = {"MP3", "WAV", "FLAC", "OGG", "M4A", "AAC"};

        for (int i = 0; i < formats.length; i++) {
            String ext = formats[i];
            String expectedType = expectedContentTypes[i];
            String expectedFormat = expectedFormatNames[i];

            byte[] dummyAudio = ("DUMMY_AUDIO_DATA_FOR_" + ext.toUpperCase()).getBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test_track." + ext,
                    expectedType,
                    dummyAudio
            );

            MvcResult uploadResult = mockMvc.perform(multipart("/api/songs/upload")
                            .file(file)
                            .param("title", "Song " + expectedFormat)
                            .param("artist", "Artist " + expectedFormat)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title", is("Song " + expectedFormat)))
                    .andExpect(jsonPath("$.artist", is("Artist " + expectedFormat)))
                    .andExpect(jsonPath("$.format", is(expectedFormat)))
                    .andExpect(jsonPath("$.contentType", is(expectedType)))
                    .andReturn();

            Number songId = com.jayway.jsonpath.JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

            // Test stream với token trong query param và Range header
            mockMvc.perform(get("/api/songs/stream/" + songId)
                            .param("token", token)
                            .header(HttpHeaders.RANGE, "bytes=0-10"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, expectedType));
        }
    }

    @Test
    void testRejectUnsupportedFormat() throws Exception {
        byte[] dummyData = "SOME_TEXT_DATA".getBytes();
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                dummyData
        );

        mockMvc.perform(multipart("/api/songs/upload")
                        .file(unsupportedFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadWithAlbumAndGetCover404WhenNoCover() throws Exception {
        byte[] dummyAudio = "DUMMY_MP3_DATA".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "track.mp3",
                "audio/mpeg",
                dummyAudio
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/songs/upload")
                        .file(file)
                        .param("title", "Album Song")
                        .param("artist", "Top Artist")
                        .param("album", "Best of 2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Album Song")))
                .andExpect(jsonPath("$.artist", is("Top Artist")))
                .andExpect(jsonPath("$.album", is("Best of 2026")))
                .andReturn();

        Number songId = com.jayway.jsonpath.JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/songs/cover/" + songId)
                        .param("token", token))
                .andExpect(status().isNotFound());
    }
}

