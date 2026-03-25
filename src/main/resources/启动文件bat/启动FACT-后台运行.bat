@echo off
chcp 65001 >nul
title FACT 贷款管理系统启动（后台运行）

echo ========================================
echo    FACT 贷款管理系统启动脚本（后台运行）
echo ========================================
echo.

REM 设置JAR包路径
set JAR_PATH=D:\springbooot\FACT-0.0.1-SNAPSHOT.jar

REM 检查JAR包是否存在
if not exist "%JAR_PATH%" (
    echo [错误] JAR包不存在: %JAR_PATH%
    echo 请检查JAR包路径是否正确！
    pause
    exit /b 1
)

echo [信息] JAR包路径: %JAR_PATH%
echo.

REM 检查Java环境
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Java环境，请确保Java已正确安装并配置环境变量！
    pause
    exit /b 1
)

echo [信息] Java环境检查通过
echo.

REM 创建日志目录（如果不存在）
if not exist "D:\springbooot\Log\allLog" (
    echo [信息] 创建全量日志目录: D:\springbooot\Log\allLog
    mkdir "D:\springbooot\Log\allLog" >nul 2>&1
)
if not exist "D:\springbooot\Log\error" (
    echo [信息] 创建错误日志目录: D:\springbooot\Log\error
    mkdir "D:\springbooot\Log\error" >nul 2>&1
)
echo.

REM 设置JVM参数
set JAVA_OPTS=-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m

echo [信息] 正在后台启动应用...
echo [信息] 应用端口: 8089
echo [信息] 全量日志路径: D:\springbooot\Log\allLog
echo [信息] 错误日志路径: D:\springbooot\Log\error
echo [提示] 应用将在后台运行，请查看日志文件查看运行状态
echo.

REM 启动应用（后台运行，不显示窗口）
start "FACT应用" /min java %JAVA_OPTS% -jar "%JAR_PATH%"

echo [信息] 应用已在后台启动
echo [提示] 可以通过任务管理器查看Java进程
echo [提示] 查看日志:
echo   全量日志: D:\springbooot\Log\allLog\application.log
echo   错误日志: D:\springbooot\Log\error\error.log
echo.
echo ========================================
echo.

timeout /t 3 >nul

