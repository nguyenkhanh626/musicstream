package com.example.musicstream.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomActionMessage {
    // Action types: PLAY, PAUSE, SEEK, CHANGE_SONG, SYNC_REQUEST, MEMBER_JOINED, MEMBER_LEFT, ROOM_CLOSED, CHAT
    @JsonAlias("actionType")
    private String type;

    private String roomId;
    private Long songId;
    private String songTitle;
    private String songArtist;
    private Double currentTime;
    private Boolean isPlaying;
    private String senderUsername;
    private String senderDisplayName;
    private String token;

    @JsonAlias("chatMessage")
    private String text;

    private Long timestamp;

    @JsonProperty("actionType")
    public String getActionType() {
        return type;
    }

    public void setActionType(String actionType) {
        this.type = actionType;
    }

    @JsonProperty("chatMessage")
    public String getChatMessage() {
        return text;
    }

    public void setChatMessage(String chatMessage) {
        this.text = chatMessage;
    }
}
