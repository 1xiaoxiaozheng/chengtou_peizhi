@echo off
chcp 65001 >nul
title FACT 贷款管理系统启动

echo ========================================
echo    FACT 贷款管理系统启动脚本
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

REM 设置Java路径（如果Java环境变量已配置，可以注释掉下面这行）
REM set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
REM set PATH=%JAVA_HOME%\bin;%PATH%

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

REM 设置应用参数（如果需要指定配置文件等）
REM set SPRING_OPTS=--spring.config.location=classpath:/application.properties

echo [信息] 正在启动应用...
echo [信息] 应用端口: 8089
echo [信息] 全量日志路径: D:\springbooot\Log\allLog
echo [信息] 错误日志路径: D:\springbooot\Log\error
echo.
echo ========================================
echo.

REM 启动应用
java %JAVA_OPTS% -jar "%JAR_PATH%" %SPRING_OPTS%

REM 如果应用异常退出，显示错误信息
if errorlevel 1 (
    echo.
    echo ========================================
    echo [错误] 应用启动失败或异常退出！
    echo 请检查日志文件:
    echo   全量日志: D:\springbooot\Log\allLog\application.log
    echo   错误日志: D:\springbooot\Log\error\error.log
    echo ========================================
    pause
    exit /b 1
)

pause

