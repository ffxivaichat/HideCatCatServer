package com.hidecatcat.server.ws;

/**
 * 消息广播接口 — 解耦 GameService 和 WebSocket Handler。
 */
public interface MessageBroadcaster {

    /** 向同口令的所有连接广播消息 */
    void broadcast(String password, Object message);

    /** 向单个连接发送消息 */
    void send(String sessionId, Object message);
}
