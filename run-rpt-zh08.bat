@echo off
chcp 65001 >nul

:: =============================================================================
:: RptZh08 报表执行脚本 (Windows版本)
:: 功能: 调用jar包执行com.example.service.RptZh08Service方法
::       默认配置从jar包内的db-config.properties读取
:: =============================================================================

setlocal EnableDelayedExpansion

:: 脚本所在目录
set "SCRIPT_DIR=%~dp0"

:: JAR包路径
set "JAR_NAME=rpt-zh08-service-1.0.0.jar"
set "JAR_PATH=%SCRIPT_DIR%target\%JAR_NAME%"

:: 解析参数
call :parse_args %*
if "%SHOW_HELP%"=="true" (
    call :show_help
    exit /b 0
)

:: 检查Java环境
call :check_java
if errorlevel 1 exit /b 1

:: 如果需要先构建
if "%BUILD_FIRST%"=="true" (
    call :build_project
    if errorlevel 1 exit /b 1
)

:: 检查JAR包
call :check_jar
if errorlevel 1 exit /b 1

:: 执行
call :execute
exit /b %errorlevel%

:: =============================================================================
:: 函数定义
:: =============================================================================

:show_help
echo RptZh08 报表执行脚本 (Windows版本)
echo.
echo 用法: %~nx0 [选项]
echo.
echo 说明:
echo   默认配置从 jar 包内的 db-config.properties 读取
echo   命令行参数可覆盖默认配置
echo.
echo 执行模式:
echo   -m, --mode MODE       执行模式: report(执行报表,默认) 或 test(测试连接)
echo.
echo 数据库连接选项 (覆盖默认配置):
echo   -h, --host HOST       数据库主机地址
echo   -P, --port PORT       数据库端口
echo   -d, --database DB     数据库名称
echo   -u, --user USER       数据库用户名
echo   -p, --password PWD    数据库密码
echo   --url URL             JDBC完整URL
echo   --driver DRIVER       JDBC驱动类
echo.
echo 报表参数:
echo   -t, --date DATE       报表日期，格式YYYYMMDD (默认: 上个月最后一天)
echo                         仅当 mode=report 时有效
echo.
echo 其他选项:
echo   --build               先执行maven打包再运行
echo   --help                显示此帮助信息
echo.
echo 示例:
echo   1. 测试数据库连接:
echo      %~nx0 -m test
echo.
echo   2. 使用配置文件执行报表，只指定日期:
echo      %~nx0 -t 20231031
echo      或显式指定:
echo      %~nx0 -m report -t 20231031
echo.
echo   3. 覆盖默认配置执行报表:
echo      %~nx0 -h 192.168.1.100 -P 3306 -d gjj_db -u root -p 123456 -t 20231031
echo.
echo   4. 使用默认日期执行报表:
echo      %~nx0
echo.
echo   5. 先打包再执行:
echo      %~nx0 --build -t 20231031
goto :eof

:parse_args
set "SHOW_HELP=false"
set "BUILD_FIRST=false"
set "MODE=report"

:parse_loop
if "%~1"=="" goto :eof

if "%~1"=="-m" (
    set "MODE=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--mode" (
    set "MODE=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-h" (
    set "DB_HOST=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--host" (
    set "DB_HOST=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-P" (
    set "DB_PORT=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--port" (
    set "DB_PORT=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-d" (
    set "DB_NAME=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--database" (
    set "DB_NAME=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-u" (
    set "DB_USER=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--user" (
    set "DB_USER=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-p" (
    set "DB_PASSWORD=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--password" (
    set "DB_PASSWORD=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--url" (
    set "JDBC_URL=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--driver" (
    set "JDBC_DRIVER=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="-t" (
    set "REPORT_DATE=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--date" (
    set "REPORT_DATE=%~2"
    shift
    shift
    goto parse_loop
)
if "%~1"=="--help" (
    set "SHOW_HELP=true"
    shift
    goto parse_loop
)
if "%~1"=="--build" (
    set "BUILD_FIRST=true"
    shift
    goto parse_loop
)

echo 未知选项: %~1
call :show_help
exit /b 1

:check_java
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Java环境，请确保已安装Java并配置了环境变量
    exit /b 1
)

echo Java版本:
java -version 2>&1
goto :eof

:build_project
echo 正在构建项目...
cd /d "%SCRIPT_DIR%"

mvn -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到mvn命令，请确保已安装Maven并配置了环境变量
    exit /b 1
)

call mvn clean package -DskipTests

if errorlevel 1 (
    echo 错误: Maven构建失败
    exit /b 1
)

echo 构建成功!
goto :eof

:check_jar
if not exist "%JAR_PATH%" (
    echo 未找到JAR包: %JAR_PATH%
    echo 需要先构建项目
    call :build_project
    if errorlevel 1 exit /b 1
)

echo 使用JAR包: %JAR_PATH%
goto :eof

:execute
echo ========================================
echo 开始执行RptZh08工具
echo ========================================

echo 执行模式: %MODE%
if "%MODE%"=="report" (
    if defined REPORT_DATE (
        echo 报表日期: %REPORT_DATE%
    ) else (
        echo 报表日期: 默认(上个月最后一天)
    )
)
echo ========================================
echo.

:: 构建命令
set "CMD=java -jar "%JAR_PATH%""

set "CMD=!CMD! --mode=%MODE%"

if defined JDBC_URL (
    set "CMD=!CMD! --url="%JDBC_URL%""
) else if defined DB_HOST (
    set "CMD=!CMD! --host=%DB_HOST%"
    if defined DB_PORT (
        set "CMD=!CMD! --port=%DB_PORT%"
    )
    if defined DB_NAME (
        set "CMD=!CMD! --database=%DB_NAME%"
    )
)

if defined DB_USER (
    set "CMD=!CMD! --user=%DB_USER%"
)

if defined DB_PASSWORD (
    set "CMD=!CMD! --password=%DB_PASSWORD%"
)

if defined JDBC_DRIVER (
    set "CMD=!CMD! --driver=%JDBC_DRIVER%"
)

if defined REPORT_DATE (
    set "CMD=!CMD! --date=%REPORT_DATE%"
)

echo 执行命令: !CMD!
echo.

call !CMD!
set "EXIT_CODE=%errorlevel%"

echo.
echo ========================================
if "%EXIT_CODE%"=="0" (
    echo 执行成功!
) else (
    echo 执行失败，退出码: %EXIT_CODE%
)
echo ========================================

exit /b %EXIT_CODE%
