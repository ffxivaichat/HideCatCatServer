package com.hidecatcat.server.ws;

/**
 * WebSocket 消息类型枚举。
 * 客户端 ↔ 服务器通信协议。
 */
public enum MessageType {

    // ---- 房间管理 ----
    JOIN_ROOM,          // C→S: 加入房间 {password, playerName, team}
    UPDATE_SETTINGS,    // C→S: 房主更新设置 {password, startPos, radius, winCondition, winCount, timeLimitMin}
    PLAYER_LIST,        // S→C: 推送玩家列表 {players, host, gameState, settings}

    // ---- 准备 & 开始 ----
    READY,              // C→S: 玩家准备 {password}
    ALL_READY,          // S→C: 所有玩家已准备
    START_GAME,         // C→S: 房主开始游戏 {password}

    // ---- 游戏进行中 ----
    POSITION_UPDATE,    // C→S: 上报坐标 {password, position:{x,y,z}}
    CATCH_EVENT,        // S→C: 抓捕事件 {catName, mouseName, miceRemaining, miceTotal}
    GAME_OVER,          // S→C: 游戏结束 {winner, reason, matchStats, playerDetails}

    // ---- 统计查询 ----
    STATS_QUERY,        // C→S: 查询个人统计 {playerName}
    STATS_RESULT,       // S→C: 返回统计结果

    // ---- 通用 ----
    ERROR               // S→C: 错误 {message}
}
