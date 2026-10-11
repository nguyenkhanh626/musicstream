package com.example.musicstream.dto;

import com.example.musicstream.model.Song;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomStateDto {
    private String roomId;

    @JsonAlias("name")
    private String roomName;

    private String inviteCode;
    private Long hostId;
    private String hostName;

    private Long currentSongId;
    private Song currentSong;
    private Double currentTime;
    private Boolean isPlaying;
    private Long lastActionTimestamp;

    private List<RoomMemberDto> members;
    private boolean active;

    @JsonProperty("name")
    public String getName() {
        return roomName;
    }

    @JsonProperty("currentSongTitle")
    public String getCurrentSongTitle() {
        return currentSong != null ? currentSong.getTitle() : null;
    }

    @JsonProperty("currentSongArtist")
    public String getCurrentSongArtist() {
        return currentSong != null ? currentSong.getArtist() : null;
    }
}
