package com.hidecatcat.server.repository;

import com.hidecatcat.server.entity.MatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

    List<MatchHistory> findByPasswordOrderByStartTimeDesc(String password);
}
