package com.example.musicstream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMemberDto {
    private Long userId;
    private String username;
    private String displayName;
    private boolean isHost;

    @JsonProperty("id")
    public Long getId() {
        return userId;
    }
}
