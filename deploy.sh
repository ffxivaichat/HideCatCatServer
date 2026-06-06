#!/bin/bash
# ================================================================
# HideCatCatServer 一键部署脚本
# 用法: ./deploy.sh
# ================================================================
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_FILE="$PROJECT_DIR/app.log"
PID_FILE="$PROJECT_DIR/app.pid"

echo "=============================================="
echo " HideCatCatServer 部署脚本"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================="

# 1. 停止旧进程
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "[1/4] 停止旧进程 PID=$OLD_PID ..."
        kill "$OLD_PID"
        sleep 2
        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "      强制终止..."
            kill -9 "$OLD_PID"
        fi
        echo "      已停止"
    else
        echo "[1/4] 旧进程已退出"
    fi
    rm -f "$PID_FILE"
else
    echo "[1/4] 无旧进程"
fi

# 2. 拉取最新代码
echo "[2/4] 拉取最新代码..."
cd "$PROJECT_DIR"
git pull

# 3. 编译
echo "[3/4] 编译..."
mvn clean package -DskipTests -q

# 4. 启动
echo "[4/4] 启动服务..."
JAR_FILE=$(ls target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "错误: 找不到 jar 文件"
    exit 1
fi

nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"

sleep 2
if kill -0 "$NEW_PID" 2>/dev/null; then
    echo ""
    echo "=============================================="
    echo " ✅ 部署成功"
    echo "    PID: $NEW_PID"
    echo "    端口: 8081"
    echo "    日志: $LOG_FILE"
    echo "=============================================="
else
    echo ""
    echo "=============================================="
    echo " ❌ 启动失败，查看日志:"
    echo "    tail -50 $LOG_FILE"
    echo "=============================================="
    tail -20 "$LOG_FILE"
    exit 1
fi
