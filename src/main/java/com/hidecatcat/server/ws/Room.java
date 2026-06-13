package com.hidecatcat.server.ws;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 房间状态。按口令分组，管理玩家列表、设置、游戏状态。
 */
public class Room {

    public enum Team { CAT, MOUSE }
    public enum GameState { WAITING, ALL_READY, PLAYING, FINISHED }

    private final String password;
    private String hostSessionId;
    private String hostName;
    private GameState state = GameState.WAITING;

    // 房间基准：第一个加入玩家的服务器和地图
    private String roomServer;
    private long territoryId;

    /** sessionId → Player */
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    // 房主设置（开始后锁定）
    private Settings settings = new Settings();
    private boolean settingsLocked;

    // 游戏倒计时定时器（房间销毁时取消）
    private ScheduledFuture<?> timerFuture;
    // 僵尸连接清理定时器（每 5 秒检查一次）
    private ScheduledFuture<?> cleanupFuture;

    // 房间级别胜负累计（重新开始不清零，房间销毁时清零）
    private int catWins = 0;
    private int mouseWins = 0;

    public Room(String password) {
        this.password = password;
    }

    public String getPassword() { return password; }
    public String getHostSessionId() { return hostSessionId; }
    public String getHostName() { return hostName; }
    public GameState getState() { return state; }
    public String getRoomServer() { return roomServer; }
    public void setRoomServer(String s) { this.roomServer = s; }
    public long getTerritoryId() { return territoryId; }
    public void setTerritoryId(long id) { this.territoryId = id; }
    public void setState(GameState state) { this.state = state; }
    public Settings getSettings() { return settings; }
    public boolean isSettingsLocked() { return settingsLocked; }

    /** 加入玩家。第一个加入的自动成为房主。 */
    public Player addPlayer(String sessionId, String name, Team team) {
        var player = new Player(sessionId, name, team);
        players.put(sessionId, player);
        if (hostSessionId == null) {
            hostSessionId = sessionId;
            hostName = name;
        }
        return player;
    }

    /** 移除玩家。房主离开时转移给下一个。 */
    public Player removePlayer(String sessionId) {
        var removed = players.remove(sessionId);
        if (sessionId.equals(hostSessionId) && !players.isEmpty()) {
            var next = players.values().iterator().next();
            hostSessionId = next.sessionId;
            hostName = next.name;
        }
        return removed;
    }

    public Collection<Player> getPlayers() { return players.values(); }
    public int playerCount() { return players.size(); }
    public boolean isEmpty() { return players.isEmpty(); }

    /** 所有玩家都已准备 */
    public boolean allReady() {
        return !players.isEmpty() && players.values().stream().allMatch(p -> p.ready);
    }

    /** 锁定设置（开始游戏时调用） */
    public void lockSettings() { settingsLocked = true; }
    /** 解锁设置（重置房间时调用） */
    public void unlockSettings() { settingsLocked = false; }

    public ScheduledFuture<?> getTimerFuture() { return timerFuture; }
    public void setTimerFuture(ScheduledFuture<?> f) { this.timerFuture = f; }
    public ScheduledFuture<?> getCleanupFuture() { return cleanupFuture; }
    public void setCleanupFuture(ScheduledFuture<?> f) { this.cleanupFuture = f; }

    public int getCatWins() { return catWins; }
    public int getMouseWins() { return mouseWins; }
    public void incrementCatWins() { catWins++; }
    public void incrementMouseWins() { mouseWins++; }

    /** 取消所有定时器（房间销毁、新游戏开始或游戏结束时调用） */
    public void cancelTimers() {
        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(false);
        }
        if (cleanupFuture != null && !cleanupFuture.isDone()) {
            cleanupFuture.cancel(false);
        }
    }

    /** 猫队人数 */
    public long catCount() {
        return players.values().stream().filter(p -> p.team == Team.CAT && !p.eliminated).count();
    }

    /** 鼠队人数（存活） */
    public long mouseCount() {
        return players.values().stream().filter(p -> p.team == Team.MOUSE && !p.eliminated).count();
    }

    /** 找出指定玩家 */
    public Player findPlayer(String sessionId) {
        return players.get(sessionId);
    }

    /** 构建 player_list 消息 */
    public Map<String, Object> buildPlayerListMessage() {
        var list = players.values().stream()
                .map(p -> {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("name", p.name);
                    m.put("team", p.team.name());
                    m.put("ready", p.ready);
                    m.put("eliminated", p.eliminated);
                    m.put("isHost", p.sessionId.equals(hostSessionId));
                    m.put("x", p.x);
                    m.put("y", p.y);
                    m.put("z", p.z);
                    return m;
                }).toList();
        var msg = new java.util.LinkedHashMap<String, Object>();
        msg.put("type", "player_list");
        msg.put("hostName", hostName);
        msg.put("gameState", state.name());
        msg.put("roomServer", roomServer != null ? roomServer : "");
        msg.put("roomTerritoryId", territoryId);
        msg.put("catCount", catCount());
        msg.put("mouseCount", mouseCount());
        msg.put("settingsLocked", settingsLocked);
        msg.put("settings", settings.toMap());
        msg.put("catWins", catWins);
        msg.put("mouseWins", mouseWins);
        msg.put("players", list);
        return msg;
    }

    // ---- 内部类 ----

    public static class Player {
        public final String sessionId;
        public final String name;
        public Team team;
        public boolean ready;
        public boolean eliminated;
        public double x, y, z;
        public String server;
        /** 最后一次收到位置上报的时间戳（用于僵尸连接检测） */
        public long lastPositionTime;
        /** 越界起始时间戳（0=在界内，>0=越界时刻），用于越界 5 秒自动抓获 */
        public long outOfBoundsSince;

        Player(String sessionId, String name, Team team) {
            this.sessionId = sessionId;
            this.name = name;
            this.team = team;
            this.lastPositionTime = System.currentTimeMillis();
            this.outOfBoundsSince = 0;
        }
    }

    public static class Settings {
        public double startX, startY, startZ;
        public float radius = 50f;              // 默认 50 yalms
        public String winCondition = "ALL";   // ALL / COUNT / PERCENT
        public int winCount = 1;
        public int timeLimitSec = 300;        // 默认 5 分钟

        Map<String, Object> toMap() {
            return Map.of(
                    "startPos", Map.of("x", startX, "y", startY, "z", startZ),
                    "radius", radius,
                    "winCondition", winCondition,
                    "winCount", winCount,
                    "timeLimitSec", timeLimitSec
            );
        }
    }
}
