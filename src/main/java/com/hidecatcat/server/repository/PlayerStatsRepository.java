package com.hidecatcat.server.repository;

import com.hidecatcat.server.entity.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerStatsRepository extends JpaRepository<PlayerStats, Long> {

    Optional<PlayerStats> findByPlayerName(String playerName);

    Optional<PlayerStats> findByPlayerNameAndPlayerServer(String playerName, String playerServer);
}
