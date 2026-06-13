package com.hidecatcat.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hidecatcat.server.repository.MatchHistoryRepository;
import com.hidecatcat.server.repository.MatchPlayerDetailRepository;
import com.hidecatcat.server.repository.PlayerStatsRepository;
import com.hidecatcat.server.ws.MessageBroadcaster;
import com.hidecatcat.server.ws.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private MessageBroadcaster broadcaster;
    private final MatchHistoryRepository matchRepo;
    private final MatchPlayerDetailRepository detailRepo;
    private final PlayerStatsRepository statsRepo;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** password → Room */
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    /** sessionId → password */
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();

    @Value("${game.max-rooms:100}")
    private int maxRooms;

    public GameService(MatchHistoryRepository matchRepo,
                       MatchPlayerDetailRepository detailRepo,
                       PlayerStatsRepository statsRepo) {
        this.matchRepo = matchRepo;
        this.detailRepo = detailRepo;
        this.statsRepo = statsRepo;
    }

    @Autowired
    public void setBroadcaster(@Lazy MessageBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    // ================================================================
    // 房间管理
    // ================================================================

    /**
     * 玩家加入房间。
     * @return true 加入成功，false 被拒绝（房间数已达上限）
     */
    public boolean onPlayerJoin(String password, String sessionId) {
        // 新房间 & 已达上限 → 拒绝
        if (!rooms.containsKey(password) && rooms.size() >= maxRooms) {
            log.warn("[房间] 已达上限 {} 拒绝新房间 password={}", maxRooms, password);
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "服务器房间已满，请稍后再试"));
            return false;
        }

        var room = rooms.computeIfAbsent(password, Room::new);
        sessionRooms.put(sessionId, password);

        log.info("[房间] {} 加入房间 password={} 当前人数={} 总房间数={}",
                sessionId, password, room.playerCount(), rooms.size());
        return true;
    }

    /** 玩家断开 */
    public void onPlayerLeave(String password, String sessionId) {
        sessionRooms.remove(sessionId);
        var room = rooms.get(password);
        if (room == null) return;

        var player = room.findPlayer(sessionId);
        var name = player != null ? player.name : sessionId;

        // 修复 Bug #2：游戏进行中最后一人掉线，先判定胜负、广播 GAME_OVER、写库，再销毁房间
        if (room.getState() == Room.GameState.PLAYING && room.playerCount() == 1 && player != null) {
            long eliminated = room.getPlayers().stream()
                    .filter(p -> p.team == Room.Team.MOUSE && p.eliminated).count();
            // 最后一人是猫 → 鼠队胜（猫全部掉线）；是鼠 → 猫队胜（鼠掉线视为被抓获）
            String winner = player.team == Room.Team.CAT ? "MOUSE" : "CAT";
            long catches = player.team == Room.Team.MOUSE ? eliminated + 1 : eliminated;
            String reason = player.team == Room.Team.CAT
                    ? "猫队全部掉线，鼠队获胜"
                    : "最后一只鼠掉线，猫队获胜";
            endGame(room, winner, catches, reason);
        }

        room.removePlayer(sessionId);
        log.info("[房间] {} ({}) 离开房间 password={} 剩余人数={}", sessionId, name, password, room.playerCount());

        if (room.isEmpty()) {
            room.cancelTimers();
            rooms.remove(password);
            log.info("[房间] password={} 已销毁", password);
        } else {
            broadcaster.broadcast(password, room.buildPlayerListMessage());
            // 玩家离开后检查猫队是否已达成胜利条件
            // （修复：全部鼠掉线后小鼠数量=0，无需等定时器走完）
            if (room.getState() == Room.GameState.PLAYING) {
                checkWinCondition(room);
            }
        }
    }

    // ── 安全 JSON 访问工具 ──
    private static String safeStr(JsonObject json, String key, String def) {
        var el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : def;
    }
    private static int safeInt(JsonObject json, String key, int def) {
        var el = json.get(key);
        try { return el != null ? el.getAsInt() : def; } catch (Exception e) { return def; }
    }
    private static double safeDouble(JsonObject json, String key, double def) {
        var el = json.get(key);
        try { return el != null ? el.getAsDouble() : def; } catch (Exception e) { return def; }
    }
    private static float safeFloat(JsonObject json, String key, float def) {
        var el = json.get(key);
        try { return el != null ? el.getAsFloat() : def; } catch (Exception e) { return def; }
    }

    /** 处理消息分发 */
    public void handleMessage(String password, String sessionId, JsonObject json) {
        var type = safeStr(json, "type", "");
        if (type.isEmpty()) return;
        var room = rooms.get(password);
        if (room == null) return;

        switch (type) {
            case "JOIN_ROOM" -> handleJoinRoom(room, sessionId, json);
            case "UPDATE_SETTINGS" -> handleUpdateSettings(room, sessionId, json);
            case "READY" -> handleReady(room, sessionId);
            case "START_GAME" -> handleStartGame(room, sessionId, json);
            case "RESET_ROOM" -> handleResetRoom(room, sessionId, json);
            case "POSITION_UPDATE" -> handlePositionUpdate(room, sessionId, json);
            case "STATS_QUERY" -> handleStatsQuery(sessionId, json);
            default -> log.debug("[消息] 未处理类型: {}", type);
        }
    }

    private void handleJoinRoom(Room room, String sessionId, JsonObject json) {
        // 游戏已开始或已结束 → 拒绝加入
        if (room.getState() == Room.GameState.PLAYING || room.getState() == Room.GameState.FINISHED) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "游戏已开始，无法加入"));
            log.warn("[加入] 拒绝 session={} 房间已在进行中 password={}", sessionId, room.getPassword());
            return;
        }

        var name = safeStr(json, "playerName", "");
        var teamStr = safeStr(json, "team", "");
        Room.Team team;
        try { team = Room.Team.valueOf(teamStr); } catch (IllegalArgumentException e) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "无效的阵营: " + teamStr));
            return;
        }

        var playerServer = safeStr(json, "playerServer", "");
        var playerTerritory = safeInt(json, "territoryId", 0);

        // 第一个加入者设定房间基准服务器/地图
        if (room.playerCount() == 0) {
            room.setRoomServer(playerServer);
            room.setTerritoryId(playerTerritory);
            log.info("[房间] password={} 基准服务器={} 地图={}", room.getPassword(), playerServer, playerTerritory);
        }

        // 校验同服同图
        if (!playerServer.equals(room.getRoomServer())) {
            broadcaster.send(sessionId, Map.of("type", "ERROR",
                    "message", "无法加入：你所在的服务器(" + playerServer + ")与房间(" + room.getRoomServer() + ")不一致"));
            return;
        }
        if (playerTerritory != room.getTerritoryId()) {
            broadcaster.send(sessionId, Map.of("type", "ERROR",
                    "message", "无法加入：你所在的地图(" + playerTerritory + ")与房间(" + room.getTerritoryId() + ")不一致"));
            return;
        }

        var player = room.addPlayer(sessionId, name, team);
        player.server = playerServer;
        log.info("[加入] {}@{} 选择 {} 队 房间 password={}", name, player.server, team, room.getPassword());

        // 新玩家自己收到完整列表
        broadcaster.send(sessionId, room.buildPlayerListMessage());
        // 其他玩家也刷新列表
        broadcastExcept(room, sessionId, room.buildPlayerListMessage());

        // 检查是否所有人都选了同一边 → 直接结束
        if (room.getState() == Room.GameState.WAITING && room.playerCount() >= 2) {
            if (room.catCount() == 0 || room.mouseCount() == 0) {
                log.info("[结束] password={} 所有人选了同一阵营，游戏无法进行", room.getPassword());
                room.setState(Room.GameState.FINISHED);
                broadcaster.broadcast(room.getPassword(), Map.of(
                        "type", "GAME_OVER",
                        "winner", "NONE",
                        "reason", "所有人选了同一阵营",
                        "catCatches", 0,
                        "mouseSurvivors", 0
                ));
            }
        }
    }

    private void handleUpdateSettings(Room room, String sessionId, JsonObject json) {
        // 只有房主可以改设置
        if (!sessionId.equals(room.getHostSessionId())) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "只有房主可以修改设置"));
            return;
        }
        // 开始后锁定
        if (room.isSettingsLocked()) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "游戏已开始，设置已锁定"));
            return;
        }

        var settings = room.getSettings();
        var pos = json.getAsJsonObject("startPos");
        if (pos != null) {
            settings.startX = safeDouble(pos, "x", settings.startX);
            settings.startY = safeDouble(pos, "y", settings.startY);
            settings.startZ = safeDouble(pos, "z", settings.startZ);
        }
        settings.radius = safeFloat(json, "radius", settings.radius);
        var wc = safeStr(json, "winCondition", "");
        if (!wc.isEmpty()) settings.winCondition = wc;
        settings.winCount = safeInt(json, "winCount", settings.winCount);
        settings.timeLimitSec = safeInt(json, "timeLimitSec", settings.timeLimitSec);

        log.info("[设置] password={} 起点=({},{},{}) 半径={} 胜利={}:{} 时间={}s",
                room.getPassword(), settings.startX, settings.startY, settings.startZ,
                settings.radius, settings.winCondition, settings.winCount, settings.timeLimitSec);

        // 广播更新后的玩家列表（含新设置）
        broadcaster.broadcast(room.getPassword(), room.buildPlayerListMessage());
    }

    private void handleReady(Room room, String sessionId) {
        var player = room.findPlayer(sessionId);
        if (player == null) return;

        player.ready = true;
        log.info("[准备] {} 已准备 房间 password={}", player.name, room.getPassword());

        // 广播刷新列表
        broadcaster.broadcast(room.getPassword(), room.buildPlayerListMessage());

        // 检查是否全部准备
        if (room.allReady()) {
            room.setState(Room.GameState.ALL_READY);
            log.info("[准备] password={} 全部玩家已准备！", room.getPassword());
            broadcaster.broadcast(room.getPassword(), Map.of("type", "ALL_READY"));
        }
    }

    private void handleStartGame(Room room, String sessionId, JsonObject json) {
        // 只有房主
        if (!sessionId.equals(room.getHostSessionId())) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "只有房主可以开始游戏"));
            return;
        }
        // 必须全员准备
        if (!room.allReady()) {
            broadcaster.send(sessionId, Map.of("type", "ERROR", "message", "还有玩家未准备"));
            return;
        }
        // 不能重复开始
        if (room.getState() == Room.GameState.PLAYING) {
            return;
        }

        // 如果房主没有手动设置起点，使用房主当前位置作为边界中心
        var settings = room.getSettings();
        if (settings.startX == 0 && settings.startY == 0 && settings.startZ == 0) {
            var pos = json.getAsJsonObject("position");
            if (pos != null) {
                settings.startX = safeDouble(pos, "x", 0);
                settings.startY = safeDouble(pos, "y", 0);
                settings.startZ = safeDouble(pos, "z", 0);
                log.info("[开始] 自动记录房主位置作为边界中心: ({},{},{})",
                        settings.startX, settings.startY, settings.startZ);
            }
        }

        room.lockSettings();
        room.setState(Room.GameState.PLAYING);
        log.info("[开始] password={} 游戏开始！猫{}人 鼠{}人 时间={}s",
                room.getPassword(), room.catCount(), room.mouseCount(),
                room.getSettings().timeLimitSec);

        // 取消旧定时器（防止旧局定时器毒害新局）
        room.cancelTimers();
        // 启动新倒计时
        var future = scheduler.schedule(() -> onTimerExpire(room.getPassword()),
                room.getSettings().timeLimitSec, TimeUnit.SECONDS);
        room.setTimerFuture(future);
        // 启动僵尸连接清理（每 5 秒检查一次）
        startZombieCleanup(room);

        // 广播开始
        broadcaster.broadcast(room.getPassword(), Map.of(
                "type", "START_GAME",
                "startPos", Map.of("x", settings.startX, "y", settings.startY, "z", settings.startZ),
                "radius", settings.radius,
                "winCondition", settings.winCondition,
                "winCount", settings.winCount,
                "timeLimitSec", settings.timeLimitSec,
                "catCount", room.catCount(),
                "mouseCount", room.mouseCount()
        ));
    }

    private void handleResetRoom(Room room, String sessionId, JsonObject json) {
        // 重置房间：取消定时器，清空准备状态，回到 WAITING
        room.cancelTimers();
        room.unlockSettings();
        room.setState(Room.GameState.WAITING);
        room.getSettings().startX = 0;
        room.getSettings().startY = 0;
        room.getSettings().startZ = 0;
        room.getSettings().radius = 50;
        for (var p : room.getPlayers()) {
            p.ready = false;
            p.eliminated = false;
            p.x = p.y = p.z = 0;
            p.lastPositionTime = System.currentTimeMillis();
            p.outOfBoundsSince = 0;
        }

        // 支持换队：如果消息带了 team 且与当前队伍不同，更新玩家队伍
        var newTeam = safeStr(json, "team", "");
        if (!newTeam.isEmpty()) {
            var player = room.findPlayer(sessionId);
            if (player != null) {
                try {
                    var team = Room.Team.valueOf(newTeam.toUpperCase());
                    if (player.team != team) {
                        log.info("[换队] {} 从 {} 切换到 {}", player.name, player.team, team);
                        player.team = team;
                    }
                } catch (IllegalArgumentException ignored) {
                    log.warn("[换队] 无效队伍: {}", newTeam);
                }
            }
        }

        log.info("[重置] password={} 房间已重置", room.getPassword());
        broadcaster.broadcast(room.getPassword(), room.buildPlayerListMessage());
    }

    private void handlePositionUpdate(Room room, String sessionId, JsonObject json) {
        if (room.getState() != Room.GameState.PLAYING) return;

        var player = room.findPlayer(sessionId);
        if (player == null || player.eliminated) return;

        // 更新坐标
        var pos = json.getAsJsonObject("position");
        if (pos != null) {
            player.x = safeDouble(pos, "x", player.x);
            player.y = safeDouble(pos, "y", player.y);
            player.z = safeDouble(pos, "z", player.z);
            player.lastPositionTime = System.currentTimeMillis();
        }

        // 广播更新后的位置
        broadcaster.broadcast(room.getPassword(), room.buildPlayerListMessage());

        // 越界检测：鼠队玩家超出边界 5 秒自动被抓获
        if (player.team == Room.Team.MOUSE) {
            var s = room.getSettings();
            double dx = player.x - s.startX;
            double dy = player.y - s.startY;
            double dz = player.z - s.startZ;
            double distToCenter = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distToCenter > s.radius) {
                // 越界：开始/继续计时
                long now = System.currentTimeMillis();
                if (player.outOfBoundsSince == 0) {
                    player.outOfBoundsSince = now;
                }
                long elapsed = now - player.outOfBoundsSince;
                if (elapsed >= 5_000) {
                    player.eliminated = true;
                    player.outOfBoundsSince = 0;
                    long remaining = room.mouseCount();
                    log.info("[越界] {} 超出边界 {}s 自动被抓获！鼠队剩余 {}",
                            player.name, elapsed / 1000.0, remaining);
                    broadcaster.broadcast(room.getPassword(), Map.of(
                            "type", "CATCH_EVENT",
                            "catName", "边界",
                            "mouseName", player.name,
                            "miceRemaining", remaining,
                            "miceTotal", room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE).count()
                    ));
                    checkWinCondition(room);
                    return; // 已淘汰，跳过后续抓捕判定
                }
            } else {
                // 回到界内：重置计时
                player.outOfBoundsSince = 0;
            }
        }

        // 抓捕判定：猫队玩家指定了目标鼠队玩家
        var targetName = safeStr(json, "targetPlayer", "");
        if (player.team == Room.Team.CAT && !targetName.isEmpty()) {
            var target = room.getPlayers().stream()
                    .filter(p -> p.name.equals(targetName) && p.team == Room.Team.MOUSE && !p.eliminated)
                    .findFirst().orElse(null);

            if (target != null) {
                var dist = Math.sqrt(
                        Math.pow(player.x - target.x, 2) +
                        Math.pow(player.y - target.y, 2) +
                        Math.pow(player.z - target.z, 2));

                // 距离 ≤ 5 yalms → 抓住
                if (dist <= 5.0) {
                    target.eliminated = true;
                    long remaining = room.mouseCount();
                    log.info("[抓捕] {} 抓住了 {}！鼠队剩余 {}/{}",
                            player.name, target.name, remaining, room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE).count());

                    broadcaster.broadcast(room.getPassword(), Map.of(
                            "type", "CATCH_EVENT",
                            "catName", player.name,
                            "mouseName", target.name,
                            "miceRemaining", remaining,
                            "miceTotal", room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE).count()
                    ));

                    // 检查是否达成胜利条件
                    checkWinCondition(room);
                }
            }
        }
    }

    /** 检查是否满足猫队胜利条件 */
    private void checkWinCondition(Room room) {
        var settings = room.getSettings();
        long totalMice = room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE).count();
        long eliminated = totalMice - room.mouseCount();
        boolean catWins = false;

        switch (settings.winCondition) {
            case "ALL" -> catWins = room.mouseCount() == 0;
            case "COUNT" -> catWins = eliminated >= settings.winCount;
            case "PERCENT" -> {
                if (totalMice > 0) {
                    int percent = (int) (eliminated * 100 / totalMice);
                    catWins = percent >= settings.winCount;
                }
            }
        }

        if (catWins) {
            endGame(room, "CAT", eliminated, "猫队达成胜利条件");
        }
    }

    // ================================================================
    // 游戏结束
    // ================================================================

    /** 启动僵尸连接清理定时任务（每 5 秒扫描一次） */
    private void startZombieCleanup(Room room) {
        var future = scheduler.scheduleAtFixedRate(() -> {
            if (room.getState() != Room.GameState.PLAYING) return;
            var now = System.currentTimeMillis();
            var timeoutMs = 15_000L; // 15 秒无坐标 → 视为掉线
            var zombies = new java.util.ArrayList<String>();
            for (var p : room.getPlayers()) {
                if (now - p.lastPositionTime > timeoutMs) {
                    zombies.add(p.sessionId);
                }
            }
            for (var sid : zombies) {
                var p = room.findPlayer(sid);
                log.warn("[僵尸] {} ({}) {}秒无坐标，强制移除",
                        sid, p != null ? p.name : "?", timeoutMs / 1000);
                onPlayerLeave(room.getPassword(), sid);
            }
        }, 5, 5, TimeUnit.SECONDS);
        room.setCleanupFuture(future);
    }

    /** 倒计时到期 → 鼠队存活获胜 */
    private void onTimerExpire(String password) {
        var room = rooms.get(password);
        if (room == null || room.getState() != Room.GameState.PLAYING) return;

        long eliminated = room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE && p.eliminated).count();
        log.info("[时间到] password={} 鼠存活={}", password, room.mouseCount());
        endGame(room, "MOUSE", eliminated, "时间到，鼠队存活");
    }

    private void endGame(Room room, String winnerTeam, long catCatches, String reason) {
        room.cancelTimers();
        room.setState(Room.GameState.FINISHED);
        long survivors = room.mouseCount();

        // 递增房间级别胜负累计
        if ("CAT".equals(winnerTeam)) {
            room.incrementCatWins();
        } else {
            room.incrementMouseWins();
        }

        log.info("[结束] password={} 胜者={} 猫抓到={} 鼠存活={} 原因={} 猫胜={} 鼠胜={}",
                room.getPassword(), winnerTeam, catCatches, survivors, reason,
                room.getCatWins(), room.getMouseWins());

        broadcaster.broadcast(room.getPassword(), Map.of(
                "type", "GAME_OVER",
                "winner", winnerTeam,
                "catCatches", catCatches,
                "mouseSurvivors", survivors,
                "reason", reason,
                "catWins", room.getCatWins(),
                "mouseWins", room.getMouseWins()
        ));

        // 记录到数据库（异步，不阻塞）
        try {
            recordMatch(room, winnerTeam, (int) catCatches, (int) survivors);
            updatePlayerStats(room, winnerTeam);
        } catch (Exception e) {
            log.error("[统计] 写入失败", e);
        }
    }

    private void recordMatch(Room room, String winnerTeam, int catCatches, int survivors) {
        var m = new com.hidecatcat.server.entity.MatchHistory();
        var s = room.getSettings();
        m.setPassword(room.getPassword());
        m.setHostName(room.getHostName());
        m.setCatCount((int) room.getPlayers().stream().filter(p -> p.team == Room.Team.CAT).count());
        m.setMouseCount((int) room.getPlayers().stream().filter(p -> p.team == Room.Team.MOUSE).count());
        m.setWinCondition(s.winCondition);
        m.setWinCount(s.winCount);
        m.setTimeLimitSec(s.timeLimitSec);
        m.setStartPos(s.startX + "," + s.startY + "," + s.startZ);
        m.setRadius(s.radius);
        m.setWinnerTeam(winnerTeam);
        m.setCatCatches(catCatches);
        m.setMouseSurvivors(survivors);
        m.setStartTime(java.time.LocalDateTime.now());
        m.setEndTime(java.time.LocalDateTime.now());
        matchRepo.save(m);

        // 写入每人明细
        for (var p : room.getPlayers()) {
            var d = new com.hidecatcat.server.entity.MatchPlayerDetail();
            d.setMatchId(m.getId());
            d.setPlayerName(p.name);
            d.setPlayerServer(p.server != null ? p.server : "");
            d.setTeam(p.team.name());
            d.setSurvivedSec(p.team == Room.Team.MOUSE && !p.eliminated ? s.timeLimitSec : 0);
            d.setWasCaught(p.eliminated);
            d.setCaughtBy(null); // 简化：不追踪具体谁抓的
            detailRepo.save(d);
        }
    }

    private void updatePlayerStats(Room room, String winnerTeam) {
        for (var p : room.getPlayers()) {
            var server = p.server != null ? p.server : "";
            var stats = statsRepo.findByPlayerNameAndPlayerServer(p.name, server)
                    .orElseGet(() -> {
                        var ps = new com.hidecatcat.server.entity.PlayerStats();
                        ps.setPlayerName(p.name);
                        ps.setPlayerServer(server);
                        return ps;
                    });

            if (p.team == Room.Team.CAT) {
                stats.setCatGames(stats.getCatGames() + 1);
                int catches = 0; // 简化：抓捕数由明细表记录
                stats.setCatTotalCatches(stats.getCatTotalCatches() + catches);
                if (catches > stats.getCatBestGame()) stats.setCatBestGame(catches);
                if (catches == 0) stats.setCatZeroCatchGames(stats.getCatZeroCatchGames() + 1);
            } else {
                stats.setMouseGames(stats.getMouseGames() + 1);
                int survived = p.eliminated ? 0 : room.getSettings().timeLimitSec;
                if (survived > stats.getMouseLongestSurvivalSec())
                    stats.setMouseLongestSurvivalSec(survived);
                stats.setMouseTotalSurvivalSec(stats.getMouseTotalSurvivalSec() + survived);
                if (p.eliminated) stats.setMouseTimesCaught(stats.getMouseTimesCaught() + 1);
                if (winnerTeam.equals("MOUSE")) stats.setMouseEscapes(stats.getMouseEscapes() + 1);
            }
            statsRepo.save(stats);
        }
    }

    private void handleStatsQuery(String sessionId, JsonObject json) {
        var name = safeStr(json, "playerName", "");
        var server = safeStr(json, "playerServer", "");
        var stats = statsRepo.findByPlayerNameAndPlayerServer(name, server).orElse(null);
        if (stats == null) {
            broadcaster.send(sessionId, Map.of("type", "STATS_RESULT", "playerName", name, "found", false));
            return;
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("type", "STATS_RESULT");
        result.put("playerName", name);
        result.put("found", true);
        result.put("catGames", stats.getCatGames());
        result.put("catTotalCatches", stats.getCatTotalCatches());
        result.put("catAvgPerGame", stats.getCatAvgPerGame());
        result.put("catBestGame", stats.getCatBestGame());
        result.put("catZeroCatchGames", stats.getCatZeroCatchGames());
        result.put("mouseGames", stats.getMouseGames());
        result.put("mouseLongestSurvivalSec", stats.getMouseLongestSurvivalSec());
        result.put("mouseEscapes", stats.getMouseEscapes());
        result.put("mouseTimesCaught", stats.getMouseTimesCaught());
        result.put("mouseAvgSurvivalSec", stats.getMouseAvgSurvivalSec());
        broadcaster.send(sessionId, result);
    }

    // ================================================================
    // 工具
    // ================================================================

    /** 向房间内除指定玩家外的所有人广播 */
    private void broadcastExcept(Room room, String exceptSessionId, Object message) {
        for (var player : room.getPlayers()) {
            if (!player.sessionId.equals(exceptSessionId)) {
                broadcaster.send(player.sessionId, message);
            }
        }
    }
}
