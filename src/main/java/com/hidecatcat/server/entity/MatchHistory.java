package com.hidecatcat.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 比赛记录。
 */
@Entity
@Table(name = "match_history")
public class MatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 房间口令 */
    @Column(nullable = false, length = 64)
    private String password;

    /** 房主名称 */
    @Column(nullable = false, length = 64)
    private String hostName;

    /** 猫队人数 */
    @Column(nullable = false)
    private int catCount;

    /** 鼠队人数 */
    @Column(nullable = false)
    private int mouseCount;

    /** 胜利条件: ALL / COUNT / PERCENT */
    @Column(nullable = false, length = 16)
    private String winCondition;

    /** 胜利条件参数值 */
    @Column(nullable = false)
    private int winCount;

    /** 时间限制（秒） */
    @Column(nullable = false)
    private int timeLimitSec;

    /** 起点坐标 JSON: {x,y,z} */
    @Column(nullable = false, length = 128)
    private String startPos;

    /** 躲猫猫半径（yalms） */
    @Column(nullable = false)
    private float radius;

    /** 获胜队伍: CAT / MOUSE */
    @Column(length = 8)
    private String winnerTeam;

    /** 猫队总抓捕数 */
    private int catCatches;

    /** 鼠队存活数（结束时） */
    private int mouseSurvivors;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    // ---- getters & setters (手动，后续用 Lombok 或 record 可简化) ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public int getCatCount() { return catCount; }
    public void setCatCount(int catCount) { this.catCount = catCount; }
    public int getMouseCount() { return mouseCount; }
    public void setMouseCount(int mouseCount) { this.mouseCount = mouseCount; }
    public String getWinCondition() { return winCondition; }
    public void setWinCondition(String winCondition) { this.winCondition = winCondition; }
    public int getWinCount() { return winCount; }
    public void setWinCount(int winCount) { this.winCount = winCount; }
    public int getTimeLimitSec() { return timeLimitSec; }
    public void setTimeLimitSec(int timeLimitSec) { this.timeLimitSec = timeLimitSec; }
    public String getStartPos() { return startPos; }
    public void setStartPos(String startPos) { this.startPos = startPos; }
    public float getRadius() { return radius; }
    public void setRadius(float radius) { this.radius = radius; }
    public String getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(String winnerTeam) { this.winnerTeam = winnerTeam; }
    public int getCatCatches() { return catCatches; }
    public void setCatCatches(int catCatches) { this.catCatches = catCatches; }
    public int getMouseSurvivors() { return mouseSurvivors; }
    public void setMouseSurvivors(int mouseSurvivors) { this.mouseSurvivors = mouseSurvivors; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
