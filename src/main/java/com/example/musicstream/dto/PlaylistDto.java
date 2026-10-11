package com.example.musicstream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistDto {
    private Long id;
    private String name;
    private String description;
    private int songCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

