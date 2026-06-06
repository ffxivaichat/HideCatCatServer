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

    /** sessionId → Player */
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    // 房主设置（开始后锁定）
    private Settings settings = new Settings();
    private boolean settingsLocked;

    // 游戏倒计时定时器（房间销毁时取消）
    private ScheduledFuture<?> timerFuture;

    public Room(String password) {
        this.password = password;
    }

    public String getPassword() { return password; }
    public String getHostSessionId() { return hostSessionId; }
    public String getHostName() { return hostName; }
    public GameState getState() { return state; }
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

    /** 取消定时器（房间销毁或新游戏开始时调用） */
    public void cancelTimer() {
        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(false);
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
        return Map.of(
                "type", "player_list",
                "hostName", hostName,
                "gameState", state.name(),
                "catCount", catCount(),
                "mouseCount", mouseCount(),
                "settingsLocked", settingsLocked,
                "settings", settings.toMap(),
                "players", list
        );
    }

    // ---- 内部类 ----

    public static class Player {
        public final String sessionId;
        public final String name;
        public final Team team;
        public boolean ready;
        public boolean eliminated;
        public double x, y, z;
        public String server;

        Player(String sessionId, String name, Team team) {
            this.sessionId = sessionId;
            this.name = name;
            this.team = team;
        }
    }

    public static class Settings {
        public double startX, startY, startZ;
        public float radius;
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
