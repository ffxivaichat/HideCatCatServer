-- ================================================================
-- HideCatCat Server — 数据库建表语句
-- 数据库: hidecatcat
-- 引擎:   MariaDB 11.7 (兼容 MySQL 8.x)
-- ================================================================

CREATE DATABASE IF NOT EXISTS hidecatcat
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE hidecatcat;

-- ================================================================
-- 比赛记录表
-- 每局游戏的全局信息：胜负、设置、时间
-- ================================================================
CREATE TABLE IF NOT EXISTS match_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    password        VARCHAR(64)  NOT NULL                 COMMENT '房间口令',
    host_name       VARCHAR(64)  NOT NULL                 COMMENT '房主名称',
    cat_count       INT          NOT NULL                 COMMENT '猫队人数',
    mouse_count     INT          NOT NULL                 COMMENT '鼠队人数',
    win_condition   VARCHAR(16)  NOT NULL                 COMMENT '胜利条件: ALL=全部抓到 / COUNT=抓到N个 / PERCENT=抓到百分比',
    win_count       INT          NOT NULL                 COMMENT '胜利条件参数：抓到几个 / 百分比值',
    time_limit_sec  INT          NOT NULL                 COMMENT '时间限制（秒）',
    start_pos       VARCHAR(128) NOT NULL                 COMMENT '起点坐标 JSON: {x, y, z}',
    radius          FLOAT        NOT NULL                 COMMENT '躲猫猫范围半径（yalms）',
    winner_team     VARCHAR(8)   DEFAULT NULL             COMMENT '获胜队伍: CAT=猫胜 / MOUSE=鼠胜',
    cat_catches     INT          DEFAULT 0                COMMENT '猫队总抓捕数',
    mouse_survivors INT          DEFAULT 0                COMMENT '鼠队结束时存活人数',
    start_time      DATETIME     NOT NULL                 COMMENT '比赛开始时间',
    end_time        DATETIME     DEFAULT NULL             COMMENT '比赛结束时间',

    PRIMARY KEY (id),
    INDEX idx_password (password),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='比赛记录表';


-- ================================================================
-- 比赛玩家明细表
-- 每周每人的个人表现：队伍、抓捕数、存活时长、是否被抓
-- ================================================================
CREATE TABLE IF NOT EXISTS match_player_detail (
    id            BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    match_id      BIGINT      NOT NULL                 COMMENT '关联的比赛 ID',
    player_name   VARCHAR(64) NOT NULL                 COMMENT '玩家名称',
    player_server VARCHAR(64) DEFAULT NULL             COMMENT '玩家所在服务器',
    team          VARCHAR(8)  NOT NULL                 COMMENT '队伍: CAT=猫队 / MOUSE=鼠队',
    cat_catches   INT         DEFAULT 0                COMMENT '本场抓到几只鼠（仅猫队有意义）',
    survived_sec  INT         DEFAULT 0                COMMENT '本场存活秒数',
    was_caught    TINYINT(1)  DEFAULT 0                COMMENT '是否被抓: 0=存活 / 1=被抓',
    caught_by     VARCHAR(64) DEFAULT NULL             COMMENT '被谁抓住',

    PRIMARY KEY (id),
    INDEX idx_match_id (match_id),
    INDEX idx_player_name (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='比赛玩家明细表';


-- ================================================================
-- 玩家累计统计表
-- 跨场次聚合：猫队 / 鼠队双维度各项指标
-- ================================================================
CREATE TABLE IF NOT EXISTS player_stats (
    id                         BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    player_name                VARCHAR(64) NOT NULL                 COMMENT '玩家名称',
    player_server              VARCHAR(64) NOT NULL DEFAULT ''       COMMENT '玩家所在服务器',

    -- 猫队统计
    cat_games                  INT DEFAULT 0  COMMENT '当过猫的总场次',
    cat_total_catches          INT DEFAULT 0  COMMENT '累计抓到鼠的总数',
    cat_best_game              INT DEFAULT 0  COMMENT '最高一局抓到的鼠数',
    cat_zero_catch_games       INT DEFAULT 0  COMMENT '毫无收获的场次',

    -- 鼠队统计
    mouse_games                INT DEFAULT 0  COMMENT '当过鼠的总场次',
    mouse_longest_survival_sec INT DEFAULT 0  COMMENT '最长存活时长（秒）',
    mouse_escapes              INT DEFAULT 0  COMMENT '成功逃脱次数（鼠队获胜）',
    mouse_times_caught         INT DEFAULT 0  COMMENT '被抓次数',
    mouse_total_survival_sec   BIGINT DEFAULT 0 COMMENT '累计存活总秒数',

    PRIMARY KEY (id),
    UNIQUE KEY uk_player_name_server (player_name, player_server)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='玩家累计统计表';
