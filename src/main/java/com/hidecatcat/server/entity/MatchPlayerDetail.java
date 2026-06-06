package com.hidecatcat.server.entity;

import jakarta.persistence.*;

/**
 * 每局每人的比赛明细。
 */
@Entity
@Table(name = "match_player_detail")
public class MatchPlayerDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false, length = 64)
    private String playerName;

    @Column(length = 64)
    private String playerServer;

    /** 队伍: CAT / MOUSE */
    @Column(nullable = false, length = 8)
    private String team;

    /** 本场抓到几只鼠（猫队） */
    private int catCatches;

    /** 本场存活秒数 */
    private int survivedSec;

    /** 是否被抓 */
    private boolean wasCaught;

    /** 被谁抓 */
    @Column(length = 64)
    private String caughtBy;

    // ---- getters & setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getPlayerServer() { return playerServer; }
    public void setPlayerServer(String playerServer) { this.playerServer = playerServer; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public int getCatCatches() { return catCatches; }
    public void setCatCatches(int catCatches) { this.catCatches = catCatches; }
    public int getSurvivedSec() { return survivedSec; }
    public void setSurvivedSec(int survivedSec) { this.survivedSec = survivedSec; }
    public boolean isWasCaught() { return wasCaught; }
    public void setWasCaught(boolean wasCaught) { this.wasCaught = wasCaught; }
    public String getCaughtBy() { return caughtBy; }
    public void setCaughtBy(String caughtBy) { this.caughtBy = caughtBy; }
}
