package com.example;

import com.example.service.RptZh08Service;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * 命令行入口类 - 直接执行RptZh08Service
 * 支持通过命令行参数传入数据库配置和日期参数
 * 默认配置从 classpath:db-config.properties 读取
 */
public class CliApplication {

    private static final String DEFAULT_CONFIG_FILE = "db-config.properties";
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static void main(String[] args) {
        // 打印使用帮助
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            System.exit(0);
        }

        try {
            // 加载默认配置
            Properties defaultConfig = loadDefaultConfig();

            // 解析参数（命令行参数覆盖默认配置）
            CommandLineArgs cmdArgs = parseArgs(args, defaultConfig);

            // 验证必要参数
            validateArgs(cmdArgs);

            // 构建数据源
            DataSource dataSource = createDataSource(cmdArgs);

            // 构建JdbcTemplate和TransactionManager
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);

            // 创建Service实例
            RptZh08Service service = new RptZh08Service(jdbcTemplate, (DataSourceTransactionManager) transactionManager);

            int result;

            // 根据模式执行不同方法
            if ("test".equalsIgnoreCase(cmdArgs.mode)) {
                // 执行测试连接方法
                System.out.println("========================================");
                System.out.println("执行模式: 测试数据库连接");
                System.out.println("数据库: " + maskPassword(cmdArgs.jdbcUrl));
                System.out.println("========================================");

                result = service.testConnection();

                System.out.println("========================================");
                if (result == 0) {
                    System.out.println("连接测试成功!");
                } else {
                    System.out.println("连接测试失败!");
                }
                System.out.println("========================================");

            } else {
                // 默认执行报表方法
                // 确定执行日期
                String reportDate = cmdArgs.reportDate;
                if (reportDate == null || reportDate.isEmpty()) {
                    // 默认使用上个月最后一天
                    LocalDate today = LocalDate.now();
                    LocalDate lastMonthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
                    reportDate = lastMonthEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    System.out.println("未指定日期，使用默认日期: " + reportDate);
                }

                System.out.println("========================================");
                System.out.println("执行模式: 执行报表 PROC_A_RPT_ZH08");
                System.out.println("日期: " + reportDate);
                System.out.println("数据库: " + maskPassword(cmdArgs.jdbcUrl));
                System.out.println("========================================");

                // 执行存储过程
                result = service.executeProc(reportDate);

                System.out.println("========================================");
                if (result == 0) {
                    System.out.println("报表执行成功!");
                } else {
                    System.out.println("报表执行失败，错误码: " + result);
                }
                System.out.println("========================================");
            }

            // 关闭数据源
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }

            System.exit(result);

        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw.toString());
            System.exit(-1);
        }
    }

    /**
     * 加载默认配置文件
     */
    private static Properties loadDefaultConfig() {
        Properties props = new Properties();
        try (InputStream is = CliApplication.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                System.out.println("已加载默认配置: " + DEFAULT_CONFIG_FILE);
            } else {
                System.out.println("未找到默认配置文件: " + DEFAULT_CONFIG_FILE + "，将使用命令行参数或内置默认值");
            }
        } catch (IOException e) {
            System.err.println("读取默认配置失败: " + e.getMessage());
        }
        return props;
    }

    /**
     * 解析命令行参数（命令行参数优先级高于默认配置）
     */
    private static CommandLineArgs parseArgs(String[] args, Properties defaultConfig) {
        CommandLineArgs cmdArgs = new CommandLineArgs();

        // 标记是否从命令行提供了数据库连接参数
        boolean hostProvided = false;
        boolean portProvided = false;
        boolean databaseProvided = false;
        boolean urlProvided = false;

        // 先从默认配置读取
        cmdArgs.jdbcUrl = defaultConfig.getProperty("db.url");
        cmdArgs.username = defaultConfig.getProperty("db.username");
        cmdArgs.password = defaultConfig.getProperty("db.password");
        cmdArgs.driverClass = defaultConfig.getProperty("db.driver-class-name", DEFAULT_DRIVER);

        // 第一遍遍历：检查哪些参数被提供了
        for (String arg : args) {
            if (arg.startsWith("--url=")) {
                urlProvided = true;
            } else if (arg.startsWith("--host=")) {
                hostProvided = true;
            } else if (arg.startsWith("--port=")) {
                portProvided = true;
            } else if (arg.startsWith("--database=")) {
                databaseProvided = true;
            }
        }

        // 如果提供了完整的URL，或者提供了host/database，则使用命令行参数覆盖
        boolean useCmdLineDbConfig = urlProvided || (hostProvided && databaseProvided);

        // 第二遍遍历：解析参数值
        for (String arg : args) {
            if (arg.startsWith("--url=")) {
                cmdArgs.jdbcUrl = arg.substring("--url=".length());
            } else if (arg.startsWith("--user=")) {
                cmdArgs.username = arg.substring("--user=".length());
            } else if (arg.startsWith("--password=")) {
                cmdArgs.password = arg.substring("--password=".length());
            } else if (arg.startsWith("--driver=")) {
                cmdArgs.driverClass = arg.substring("--driver=".length());
            } else if (arg.startsWith("--date=")) {
                cmdArgs.reportDate = arg.substring("--date=".length());
            } else if (arg.startsWith("--host=")) {
                cmdArgs.host = arg.substring("--host=".length());
            } else if (arg.startsWith("--port=")) {
                cmdArgs.port = arg.substring("--port=".length());
            } else if (arg.startsWith("--database=")) {
                cmdArgs.database = arg.substring("--database=".length());
            } else if (arg.startsWith("--mode=")) {
                cmdArgs.mode = arg.substring("--mode=".length());
            }
        }

        // 如果使用命令行数据库配置且提供了host/database，则构建URL
        if (useCmdLineDbConfig && cmdArgs.host != null && cmdArgs.database != null) {
            String port = cmdArgs.port != null ? cmdArgs.port : "3306";
            cmdArgs.jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                    cmdArgs.host, port, cmdArgs.database);
            System.out.println("使用命令行参数构建数据库连接: " + maskPassword(cmdArgs.jdbcUrl));
        }

        return cmdArgs;
    }

    /**
     * 验证必要参数
     */
    private static void validateArgs(CommandLineArgs cmdArgs) {
        if (cmdArgs.jdbcUrl == null || cmdArgs.jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("缺少数据库URL参数，请使用 --url 或 --host/--database 指定，或在 db-config.properties 中配置");
        }
        if (cmdArgs.username == null || cmdArgs.username.isEmpty()) {
            throw new IllegalArgumentException("缺少用户名参数，请使用 --user 指定，或在 db-config.properties 中配置");
        }
        if (cmdArgs.password == null) {
            cmdArgs.password = "";
        }
    }

    /**
     * 创建数据源
     */
    private static DataSource createDataSource(CommandLineArgs cmdArgs) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(cmdArgs.driverClass != null ? cmdArgs.driverClass : DEFAULT_DRIVER);
        config.setJdbcUrl(cmdArgs.jdbcUrl);
        config.setUsername(cmdArgs.username);
        config.setPassword(cmdArgs.password);

        // 连接池配置
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // 测试连接
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    /**
     * 打印使用帮助
     */
    private static void printUsage() {
        System.out.println("RptZh08报表执行工具");
        System.out.println("用法: java -jar rpt-zh08-service-1.0.0.jar [选项]");
        System.out.println();
        System.out.println("说明:");
        System.out.println("  默认配置从 db-config.properties 读取，命令行参数可覆盖默认配置");
        System.out.println();
        System.out.println("执行模式:");
        System.out.println("  --mode=MODE        执行模式: report(执行报表,默认) 或 test(测试连接)");
        System.out.println();
        System.out.println("数据库连接选项 (覆盖默认配置):");
        System.out.println("  --url=URL          JDBC连接URL (完整URL)");
        System.out.println("  --host=HOST        数据库主机地址");
        System.out.println("  --port=PORT        数据库端口 (默认: 3306)");
        System.out.println("  --database=DB      数据库名称");
        System.out.println("  --user=USERNAME    数据库用户名");
        System.out.println("  --password=PWD     数据库密码");
        System.out.println("  --driver=DRIVER    JDBC驱动类 (默认: com.mysql.cj.jdbc.Driver)");
        System.out.println();
        System.out.println("报表参数:");
        System.out.println("  --date=YYYYMMDD    报表日期 (默认: 上个月最后一天)");
        System.out.println("                     仅当 --mode=report 时有效");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  1. 测试数据库连接:");
        System.out.println("     java -jar rpt-zh08-service-1.0.0.jar --mode=test");
        System.out.println();
        System.out.println("  2. 使用配置文件执行报表，只指定日期:");
        System.out.println("     java -jar rpt-zh08-service-1.0.0.jar --date=20231031");
        System.out.println("     或显式指定:");
        System.out.println("     java -jar rpt-zh08-service-1.0.0.jar --mode=report --date=20231031");
        System.out.println();
        System.out.println("  3. 覆盖默认配置执行报表:");
        System.out.println("     java -jar rpt-zh08-service-1.0.0.jar \\");
        System.out.println("       --url=jdbc:mysql://localhost:3306/mydb?useSSL=false \\");
        System.out.println("       --user=root --password=123456 --date=20231031");
        System.out.println();
        System.out.println("  4. 使用host/port覆盖默认配置执行报表:");
        System.out.println("     java -jar rpt-zh08-service-1.0.0.jar \\");
        System.out.println("       --host=192.168.1.100 --port=3306 --database=mydb \\");
        System.out.println("       --user=root --password=123456 --date=20231031");
        System.out.println();
        System.out.println("帮助:");
        System.out.println("  --help, -h         显示此帮助信息");
    }

    /**
     * 隐藏密码用于显示
     */
    private static String maskPassword(String url) {
        if (url == null) return "";
        // 如果URL中包含密码，隐藏它
        return url.replaceAll("(password=|pwd=)[^&]*", "password=***");
    }

    /**
     * 命令行参数封装类
     */
    private static class CommandLineArgs {
        String jdbcUrl;
        String username;
        String password;
        String driverClass;
        String reportDate;
        String host;
        String port;
        String database;
        String mode;  // 执行模式: report(默认) 或 test
    }
}
