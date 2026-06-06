# HideCatCat Server

躲猫猫 WebSocket 游戏服务器 — Spring Boot + MariaDB

## 功能

- 房间管理（创建/加入/离开）
- 实时 WebSocket 通信（坐标上报、抓捕判定）
- 游戏状态机（等待 → 准备 → 游戏中 → 结束）
- 赛后统计存储（比赛记录、玩家统计）

## ⚠️ 运行前配置

**必须修改以下内容才能正常部署：**

- `src/main/resources/application.yml` — 配置数据库连接：
  ```yaml
  datasource:
    url: jdbc:mariadb://你的数据库IP:3306/hidecatcat?...
    username: 你的用户名
    password: 你的密码
  ```

- 也可以通过环境变量覆盖（无需修改文件）：
  ```
  SPRING_DATASOURCE_URL=jdbc:mariadb://...
  SPRING_DATASOURCE_USERNAME=xxx
  SPRING_DATASOURCE_PASSWORD=xxx
  APP_PORT=8091
  ```

## 构建

```bash
mvn clean package -DskipTests
```

## 部署

```bash
java -jar target/hidecatcat-server-1.0.0.jar
```

或使用一键脚本：

```bash
./deploy.sh
```

## 项目结构

```
HideCatCatServer/
├── pom.xml
├── deploy.sh
├── sql/
│   ├── schema.sql          # 数据库建表
│   └── migrate_v3.sql      # 迁移脚本
├── src/main/java/com/hidecatcat/server/
│   ├── config/
│   │   └── WebSocketConfig.java
│   ├── entity/             # JPA 实体
│   ├── repository/         # Spring Data 仓库
│   ├── service/
│   │   └── GameService.java  # 核心游戏逻辑
│   └── ws/                 # WebSocket 处理
└── src/main/resources/
    └── application.yml     # 配置文件
```
