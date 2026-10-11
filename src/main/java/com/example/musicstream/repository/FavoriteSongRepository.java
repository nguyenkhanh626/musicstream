package com.example.musicstream.repository;

import com.example.musicstream.model.FavoriteSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteSongRepository extends JpaRepository<FavoriteSong, Long> {
    List<FavoriteSong> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<FavoriteSong> findByUserIdAndSongId(Long userId, Long songId);

    boolean existsByUserIdAndSongId(Long userId, Long songId);

    @Query("SELECT f.song.id FROM FavoriteSong f WHERE f.user.id = :userId")
    List<Long> findFavoriteSongIdsByUserId(@Param("userId") Long userId);

    void deleteBySongId(Long songId);
}

