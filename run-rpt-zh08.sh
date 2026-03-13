#!/bin/bash

# =============================================================================
# RptZh08 报表执行脚本
# 功能: 调用jar包执行com.example.service.RptZh08Service#executeProc方法
#       默认配置从jar包内的db-config.properties读取
# =============================================================================

# 脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# JAR包路径
JAR_NAME="rpt-zh08-service-1.0.0.jar"
JAR_PATH="${SCRIPT_DIR}/target/${JAR_NAME}"

# 显示帮助信息
show_help() {
    echo "RptZh08 报表执行脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "说明:"
    echo "  默认配置从 jar 包内的 db-config.properties 读取"
    echo "  命令行参数可覆盖默认配置"
    echo ""
    echo "执行模式:"
    echo "  -m, --mode MODE       执行模式: report(执行报表,默认) 或 test(测试连接)"
    echo ""
    echo "数据库连接选项 (覆盖默认配置):"
    echo "  -h, --host HOST       数据库主机地址"
    echo "  -P, --port PORT       数据库端口"
    echo "  -d, --database DB     数据库名称"
    echo "  -u, --user USER       数据库用户名"
    echo "  -p, --password PWD    数据库密码"
    echo "  --url URL             JDBC完整URL (使用此选项时忽略host/port/database)"
    echo "  --driver DRIVER       JDBC驱动类"
    echo ""
    echo "报表参数:"
    echo "  -t, --date DATE       报表日期，格式YYYYMMDD (默认: 上个月最后一天)"
    echo "                        仅当 mode=report 时有效"
    echo ""
    echo "其他选项:"
    echo "  --help                显示此帮助信息"
    echo "  --build               先执行maven打包再运行"
    echo ""
    echo "示例:"
    echo "  1. 测试数据库连接:"
    echo "     $0 --mode test"
    echo ""
    echo "  2. 使用配置文件中的默认配置执行报表，只指定日期:"
    echo "     $0 -t 20231031"
    echo "     或显式指定:"
    echo "     $0 -m report -t 20231031"
    echo ""
    echo "  3. 覆盖默认配置执行报表:"
    echo "     $0 -h 192.168.1.100 -P 3306 -d gjj_db -u root -p 123456 -t 20231031"
    echo ""
    echo "  4. 使用默认日期执行报表:"
    echo "     $0"
    echo ""
    echo "  5. 先打包再执行:"
    echo "     $0 --build -t 20231031"
}

# 解析命令行参数
parse_args() {
    # 重置标记
    PASSWORD_SET=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -m|--mode)
                MODE="$2"
                shift 2
                ;;
            -h|--host)
                DB_HOST="$2"
                shift 2
                ;;
            -P|--port)
                DB_PORT="$2"
                shift 2
                ;;
            -d|--database)
                DB_NAME="$2"
                shift 2
                ;;
            -u|--user)
                DB_USER="$2"
                shift 2
                ;;
            -p|--password)
                DB_PASSWORD="$2"
                PASSWORD_SET=true
                shift 2
                ;;
            --url)
                JDBC_URL="$2"
                shift 2
                ;;
            --driver)
                JDBC_DRIVER="$2"
                shift 2
                ;;
            -t|--date)
                REPORT_DATE="$2"
                shift 2
                ;;
            --help)
                show_help
                exit 0
                ;;
            --build)
                BUILD_FIRST=true
                shift
                ;;
            *)
                echo "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 检查Java环境
check_java() {
    if ! command -v java &> /dev/null; then
        echo "错误: 未找到Java环境，请确保已安装Java并配置了环境变量"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "Java版本: ${java_version}"
}

# 构建项目
build_project() {
    echo "正在构建项目..."
    cd "${SCRIPT_DIR}"

    if ! command -v mvn &> /dev/null; then
        echo "错误: 未找到mvn命令，请确保已安装Maven并配置了环境变量"
        exit 1
    fi

    mvn clean package -DskipTests

    if [[ $? -ne 0 ]]; then
        echo "错误: Maven构建失败"
        exit 1
    fi

    echo "构建成功!"
}

# 检查JAR包
check_jar() {
    if [[ ! -f "${JAR_PATH}" ]]; then
        echo "未找到JAR包: ${JAR_PATH}"
        echo "需要先构建项目"
        build_project
    fi

    echo "使用JAR包: ${JAR_PATH}"
}

# 构建执行命令
build_command() {
    local cmd="java -jar \"${JAR_PATH}\""

    # 添加执行模式
    if [[ -n "${MODE}" ]]; then
        cmd="${cmd} --mode=${MODE}"
    fi

    if [[ -n "${JDBC_URL}" ]]; then
        cmd="${cmd} --url=\"${JDBC_URL}\""
    elif [[ -n "${DB_HOST}" ]]; then
        cmd="${cmd} --host=${DB_HOST}"
        if [[ -n "${DB_PORT}" ]]; then
            cmd="${cmd} --port=${DB_PORT}"
        fi
        if [[ -n "${DB_NAME}" ]]; then
            cmd="${cmd} --database=${DB_NAME}"
        fi
    fi

    if [[ -n "${DB_USER}" ]]; then
        cmd="${cmd} --user=${DB_USER}"
    fi

    # 密码特殊处理：使用 -p 参数时即使为空字符串也要传递
    if [[ -n "${DB_PASSWORD}" || "${PASSWORD_SET}" == "true" ]]; then
        cmd="${cmd} --password=${DB_PASSWORD}"
    fi

    if [[ -n "${JDBC_DRIVER}" ]]; then
        cmd="${cmd} --driver=${JDBC_DRIVER}"
    fi

    if [[ -n "${REPORT_DATE}" ]]; then
        cmd="${cmd} --date=${REPORT_DATE}"
    fi

    echo "${cmd}"
}

# 调试输出：显示配置信息
print_debug_info() {
    echo ""
    echo "--- 连接配置信息 ---"
    if [[ -n "${DB_HOST}" ]]; then
        echo "主机: ${DB_HOST}"
        echo "端口: ${DB_PORT:-3306}"
        echo "数据库: ${DB_NAME}"
    elif [[ -n "${JDBC_URL}" ]]; then
        echo "JDBC URL: ${JDBC_URL}"
    else
        echo "使用配置文件中的默认数据库配置"
    fi
    if [[ -n "${DB_USER}" ]]; then
        echo "用户: ${DB_USER}"
    fi
    if [[ "${PASSWORD_SET}" == "true" ]]; then
        echo "密码: (已设置)"
    fi
    echo "模式: ${MODE:-report}"
    if [[ "${MODE:-report}" == "report" && -n "${REPORT_DATE}" ]]; then
        echo "日期: ${REPORT_DATE}"
    fi
    echo "-------------------"
    echo ""
}

# 执行报表
execute_report() {
    echo "========================================"
    echo "开始执行RptZh08工具"
    echo "========================================"

    # 设置默认模式
    MODE="${MODE:-report}"

    # 显示调试信息
    print_debug_info

    local cmd=$(build_command)
    echo "执行命令: ${cmd}"
    echo ""

    eval ${cmd}
    local exit_code=$?

    echo ""
    echo "========================================"
    if [[ ${exit_code} -eq 0 ]]; then
        echo "执行成功!"
    else
        echo "执行失败，退出码: ${exit_code}"
    fi
    echo "========================================"

    return ${exit_code}
}

# 主函数
main() {
    # 解析参数
    parse_args "$@"

    # 检查Java环境
    check_java

    # 如果需要先构建
    if [[ "${BUILD_FIRST}" == true ]]; then
        build_project
    fi

    # 检查JAR包
    check_jar

    # 执行报表
    execute_report

    exit $?
}

# 执行主函数
main "$@"
