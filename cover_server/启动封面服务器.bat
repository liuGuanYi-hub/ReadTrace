@echo off
chcp 65001 >nul
title ReadTrace 内网封面服务
echo ============================================
echo   ReadTrace 内网封面服务
echo   目录: %~dp0
echo   端口: 8000
echo ============================================
echo.
echo 本机内网 IP 地址（手机 App 中填 http://对应IP:8000 ）:
ipconfig | findstr /i "IPv4"
echo.
echo 启动后请保持本窗口开启，按 Ctrl+C 停止服务。
echo.
cd /d %~dp0
python -m http.server 8000
pause
