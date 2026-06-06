-- ================================================================
-- HideCatCat Server — 数据库迁移 v2
-- 增加 player_server 字段
-- ================================================================

USE hidecatcat;

ALTER TABLE match_player_detail
    ADD COLUMN IF NOT EXISTS player_server VARCHAR(64) DEFAULT NULL COMMENT '玩家所在服务器' AFTER player_name;

ALTER TABLE player_stats
    ADD COLUMN IF NOT EXISTS player_server VARCHAR(64) NOT NULL DEFAULT '' COMMENT '玩家所在服务器' AFTER player_name,
    DROP INDEX IF EXISTS uk_player_name,
    ADD UNIQUE INDEX uk_player_name_server (player_name, player_server);
