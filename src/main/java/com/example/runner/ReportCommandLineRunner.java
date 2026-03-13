package com.example.runner;

import com.example.service.RptZh08Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 命令行运行器 - 用于在应用启动时执行报表
 * 可以通过参数控制是否执行: --run.report=true --report.date=20231031
 */
@Component
public class ReportCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReportCommandLineRunner.class);

    private final RptZh08Service rptService;

    public ReportCommandLineRunner(RptZh08Service rptService) {
        this.rptService = rptService;
    }

    @Override
    public void run(String... args) throws Exception {
        // 检查是否需要执行报表
        boolean shouldRunReport = false;
        String reportDate = null;

        // 解析命令行参数
        for (String arg : args) {
            if (arg.startsWith("--run.report=true")) {
                shouldRunReport = true;
            }
            if (arg.startsWith("--report.date=")) {
                reportDate = arg.substring("--report.date=".length());
            }
        }

        // 也可以通过系统属性获取
        if (!shouldRunReport) {
            shouldRunReport = Boolean.getBoolean("run.report");
        }
        if (reportDate == null) {
            reportDate = System.getProperty("report.date");
        }

        if (shouldRunReport) {
            // 如果没有指定日期，默认使用上个月最后一天
            if (reportDate == null) {
                LocalDate today = LocalDate.now();
                LocalDate lastMonthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
                reportDate = lastMonthEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }

            logger.info("开始执行报表，日期: {}", reportDate);
            int result = rptService.executeProc(reportDate);

            if (result == 0) {
                logger.info("报表生成成功！");
            } else {
                logger.error("报表生成失败，错误码：{}", result);
                // 可以在这里添加邮件通知或短信告警
            }
        } else {
            logger.info("跳过报表执行。如需执行请添加参数: --run.report=true --report.date=20231031");
        }
    }
}
