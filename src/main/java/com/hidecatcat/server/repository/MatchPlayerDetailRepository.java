package com.hidecatcat.server.repository;

import com.hidecatcat.server.entity.MatchPlayerDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchPlayerDetailRepository extends JpaRepository<MatchPlayerDetail, Long> {

    List<MatchPlayerDetail> findByMatchId(Long matchId);

    List<MatchPlayerDetail> findByPlayerNameOrderByIdDesc(String playerName);
}
