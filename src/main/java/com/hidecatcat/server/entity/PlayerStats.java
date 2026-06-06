package com.hidecatcat.server.entity;

import jakarta.persistence.*;

/**
 * 玩家累计统计（猫鼠双维度）。
 */
@Entity
@Table(name = "player_stats")
public class PlayerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 玩家名 */
    @Column(nullable = false, length = 64)
    private String playerName;

    /** 玩家所在服务器 */
    @Column(nullable = false, length = 64)
    private String playerServer = "";

    // ---- 猫队统计 ----
    private int catGames;             // 当猫场次
    private int catTotalCatches;      // 累计抓到鼠数
    private int catBestGame;          // 最高一局抓到数
    private int catZeroCatchGames;    // 毫无收获场次

    // ---- 鼠队统计 ----
    private int mouseGames;           // 当鼠场次
    private int mouseLongestSurvivalSec; // 最长存活秒数
    private int mouseEscapes;         // 成功逃脱次数（鼠胜）
    private int mouseTimesCaught;     // 被抓次数
    private long mouseTotalSurvivalSec;  // 累计存活总秒数

    // ---- getters & setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getPlayerServer() { return playerServer; }
    public void setPlayerServer(String playerServer) { this.playerServer = playerServer; }
    public int getCatGames() { return catGames; }
    public void setCatGames(int catGames) { this.catGames = catGames; }
    public int getCatTotalCatches() { return catTotalCatches; }
    public void setCatTotalCatches(int catTotalCatches) { this.catTotalCatches = catTotalCatches; }
    public int getCatBestGame() { return catBestGame; }
    public void setCatBestGame(int catBestGame) { this.catBestGame = catBestGame; }
    public int getCatZeroCatchGames() { return catZeroCatchGames; }
    public void setCatZeroCatchGames(int catZeroCatchGames) { this.catZeroCatchGames = catZeroCatchGames; }
    public int getMouseGames() { return mouseGames; }
    public void setMouseGames(int mouseGames) { this.mouseGames = mouseGames; }
    public int getMouseLongestSurvivalSec() { return mouseLongestSurvivalSec; }
    public void setMouseLongestSurvivalSec(int mouseLongestSurvivalSec) { this.mouseLongestSurvivalSec = mouseLongestSurvivalSec; }
    public int getMouseEscapes() { return mouseEscapes; }
    public void setMouseEscapes(int mouseEscapes) { this.mouseEscapes = mouseEscapes; }
    public int getMouseTimesCaught() { return mouseTimesCaught; }
    public void setMouseTimesCaught(int mouseTimesCaught) { this.mouseTimesCaught = mouseTimesCaught; }
    public long getMouseTotalSurvivalSec() { return mouseTotalSurvivalSec; }
    public void setMouseTotalSurvivalSec(long mouseTotalSurvivalSec) { this.mouseTotalSurvivalSec = mouseTotalSurvivalSec; }

    // ---- 计算属性 ----

    /** 平均每局抓到（猫队） */
    public float getCatAvgPerGame() {
        return catGames > 0 ? (float) catTotalCatches / catGames : 0;
    }

    /** 平均存活秒数（鼠队） */
    public float getMouseAvgSurvivalSec() {
        return mouseGames > 0 ? (float) mouseTotalSurvivalSec / mouseGames : 0;
    }
}
