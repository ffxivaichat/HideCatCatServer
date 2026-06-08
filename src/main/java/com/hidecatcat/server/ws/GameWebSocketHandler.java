package com.hidecatcat.server.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hidecatcat.server.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 核心处理器。
 * 每个连接按 password 分组到房间，消息只广播给同口令的玩家。
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler implements MessageBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final Gson gson = new Gson();

    private final GameService gameService;

    /** sessionId → session */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** sessionId → password */
    private final Map<String, String> sessionPasswords = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var password = extractPassword(session);
        if (password == null || password.isBlank()) {
            close(session, "缺少 password 参数");
            return;
        }

        // 先检查房间上限，拒绝则在加入 maps 之前关闭连接
        if (!gameService.onPlayerJoin(password, session.getId())) {
            close(session, "服务器房间已满");
            return;
        }

        sessions.put(session.getId(), session);
        sessionPasswords.put(session.getId(), password);
        log.info("[连接] {} 加入房间 password={}", session.getId(), password);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var password = sessionPasswords.get(session.getId());
        if (password == null) return;

        try {
            var json = gson.fromJson(message.getPayload(), JsonObject.class);
            log.debug("[消息] password={} type={}", password, json.get("type").getAsString());

            gameService.handleMessage(password, session.getId(), json);

        } catch (Exception e) {
            log.error("[消息] 解析失败: {}", e.getMessage());
            sendError(session, "消息格式错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var password = sessionPasswords.remove(session.getId());
        sessions.remove(session.getId());

        if (password != null) {
            log.info("[断开] {} 离开房间 password={}", session.getId(), password);
            gameService.onPlayerLeave(password, session.getId());
        }
    }

    // ---- 广播工具 ----

    /** 向同口令的所有连接广播消息 */
    public void broadcast(String password, Object message) {
        var text = gson.toJson(message);
        // 快照避免 TOCTOU：afterConnectionClosed 可能并发删除条目
        for (var entry : new HashMap<>(sessionPasswords).entrySet()) {
            if (entry.getValue().equals(password)) {
                var session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(text));
                    } catch (Exception e) {
                        log.error("[广播] 发送失败 session={}", entry.getKey());
                    }
                }
            }
        }
    }

    /** 向单个连接发送消息 */
    public void send(String sessionId, Object message) {
        var session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(gson.toJson(message)));
            } catch (Exception e) {
                log.error("[发送] 失败 session={}", sessionId);
            }
        }
    }

    private void sendError(WebSocketSession session, String msg) {
        send(session.getId(), Map.of("type", MessageType.ERROR.name(), "message", msg));
    }

    private void close(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(reason));
        } catch (Exception ignored) {}
    }

    private String extractPassword(WebSocketSession session) {
        var query = session.getUri().getQuery();
        if (query == null) return null;
        for (var param : query.split("&")) {
            var kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals("password")) {
                return kv[1];
            }
        }
        return null;
    }
}
