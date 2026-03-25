@echo off
chcp 65001 >nul
title FACT 贷款管理系统停止脚本

echo ========================================
echo    FACT 贷款管理系统停止脚本
echo ========================================
echo.

REM 查找并停止FACT应用进程
echo [信息] 正在查找FACT应用进程...

REM 查找包含FACT-0.0.1-SNAPSHOT.jar的Java进程
for /f "tokens=2" %%i in ('jps -l ^| findstr "FACT-0.0.1-SNAPSHOT.jar"') do (
    echo [信息] 找到进程ID: %%i
    echo [信息] 正在停止进程...
    taskkill /F /PID %%i >nul 2>&1
    if errorlevel 1 (
        echo [警告] 停止进程失败，可能需要管理员权限
    ) else (
        echo [成功] 进程已停止
    )
)

REM 如果jps命令不可用，使用tasklist查找
jps -l >nul 2>&1
if errorlevel 1 (
    echo [提示] jps命令不可用，使用tasklist查找进程...
    for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FO CSV ^| findstr "java.exe"') do (
        wmic process where "ProcessId=%%i" get CommandLine 2>nul | findstr "FACT-0.0.1-SNAPSHOT.jar" >nul
        if not errorlevel 1 (
            echo [信息] 找到进程ID: %%i
            echo [信息] 正在停止进程...
            taskkill /F /PID %%i >nul 2>&1
            if errorlevel 1 (
                echo [警告] 停止进程失败，可能需要管理员权限
            ) else (
                echo [成功] 进程已停止
            )
        )
    )
)

echo.
echo [信息] 停止操作完成
echo ========================================
echo.

pause

