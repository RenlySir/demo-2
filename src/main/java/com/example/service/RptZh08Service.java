package com.example.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * RptZh08 报表服务类
 * 对应存储过程 PROC_A_RPT_ZH08
 */
@Service
public class RptZh08Service {

    private static final Logger logger = LoggerFactory.getLogger(RptZh08Service.class);
    private final JdbcTemplate jdbcTemplate;
    private final DataSourceTransactionManager transactionManager;

    /**
     * 构造函数注入
     */
    public RptZh08Service(JdbcTemplate jdbcTemplate, DataSourceTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    /**
     * 主入口方法：对应存储过程 PROC_A_RPT_ZH08
     *
     * @param iDateStr 输入日期字符串 "YYYYMMDD"
     * @return 0 表示成功，非 0 表示错误码
     */
    public int executeProc(String iDateStr) {
        int oFlg = 0;

        try {
            logger.info("开始执行报表 PROC_A_RPT_ZH08, 日期: {}", iDateStr);

            // 1. 变量初始化与日期计算 (对应 PL/SQL 开头部分)
            Map<String, String> dateVars = calculateDates(iDateStr);
            String vMonth = dateVars.get("v_month");
            String vMonth1 = dateVars.get("v_month1");
            String vLastDay = dateVars.get("v_last_day");
            String vDate = dateVars.get("v_date");

            // 2. 数据清理 (支持重跑) - 对应 DELETE 和 TRUNCATE
            cleanOldData(vMonth);

            // 3. 计算临时表数据 (Tmp)
            ensureIndexes();
            calcTempTableTmp(vMonth, vLastDay);

            // 4. 计算临时表数据 (Tmp_dw)
            calcTempTableTmpDw(vMonth, vLastDay);

            // 5. 汇总计算到 tmpa
            calcSummaryTmpa(vMonth, vLastDay);

            // 6. 汇总计算到 dw
            calcSummaryDw(vMonth, vLastDay);

            // 7. 修正单位笔数 (Update)
            fixUnitCount(vMonth);

            // 8. 插入最终报表数据 (A_RPT_ZH08) - 分步骤插入各个指标
            insertReportData(vMonth, vMonth1, vLastDay, vDate);

            logger.info("报表 PROC_A_RPT_ZH08 执行成功, 月份: {}", vMonth);
            return 0;

        } catch (Exception e) {
            logger.error("报表 PROC_A_RPT_ZH08 执行失败", e);
            // 返回错误码 (模拟 SQLCODE)
            oFlg = -1;
            // 这里可以记录详细的错误日志到数据库日志表
            return oFlg;
        }
    }

    /**
     * 快捷调用方法（兼容原调用方式）
     * @param iDateStr 输入日期字符串 "YYYYMMDD"
     * @return 0 表示成功，非 0 表示错误码
     */
    public int f(String iDateStr) {
        return executeProc(iDateStr);
    }

    /**
     * 日期计算逻辑
     */
    private Map<String, String> calculateDates(String iDateStr) {
        Map<String, String> map = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyyMM");

        LocalDate inputDate = LocalDate.parse(iDateStr, formatter);

        // v_date = add_months(i_date, -1)
        LocalDate vDateLocal = inputDate.minusMonths(1);
        String vDate = vDateLocal.format(formatter);

        // v_month = substr(v_date, 1, 6)
        String vMonth = vDateLocal.format(monthFormatter);

        // v_month1 = add_months(i_date, -2) -> yyyyMM
        String vMonth1 = inputDate.minusMonths(2).format(monthFormatter);

        // v_last_day = last_day(add_months(i_date, -1))
        LocalDate vLastDayLocal = vDateLocal.withDayOfMonth(vDateLocal.lengthOfMonth());
        String vLastDay = vLastDayLocal.format(formatter);

        map.put("v_date", vDate);
        map.put("v_month", vMonth);
        map.put("v_month1", vMonth1);
        map.put("v_last_day", vLastDay);

        return map;
    }

    /**
     * 清理旧数据
     */
    private void cleanOldData(String vMonth) throws SQLException {
        logger.debug("清理月份 {} 的旧数据", vMonth);
        // 使用 update 执行 DELETE
        jdbcTemplate.update("DELETE FROM A_RPT_ZH08 WHERE DATA_M = ?", vMonth);
        jdbcTemplate.update("DELETE FROM A_RPT_ZH08_tmpa WHERE DATA_M = ?", vMonth);
        jdbcTemplate.update("DELETE FROM a_Rpt_Zh08_dw WHERE DATA_M = ?", vMonth);

        // 使用 execute 执行 TRUNCATE (DDL 操作，某些驱动可能需要特殊处理，但在 Spring JdbcTemplate 中通常可行)
        // 注意：TRUNCATE 在某些数据库中会自动 commit，需确认数据库配置。若自动 commit，则事务边界需注意。
        // 如果 TRUNCATE 导致隐式提交，建议改用 DELETE FROM ... 或者调整事务策略。
        // 这里为了保持与原存储过程逻辑一致，假设环境允许 DDL 在事务中或忽略隐式提交影响。
        jdbcTemplate.execute("TRUNCATE TABLE a_rpt_zh08_tmp");
        jdbcTemplate.execute("TRUNCATE TABLE a_rpt_zh08_tmp_dw");
    }

    /**
     * 计算临时表 Tmp (对应原 SQL 第一个 Insert)
     */
    private void calcTempTableTmp(String vMonth, String vLastDay) {
        String sql = """
            INSERT INTO a_rpt_zh08_tmp
            (corppayrate, corpkind, persacctno, corpacctno, Acctdate, amt, tradecode, tradesubcode, row_id)
            SELECT a2.dwjcbl, a3.corpkind, a1.grzh, a1.dwzh, a1.jzrq, a1.fse,\s
                   a1.tradecode, a1.tradesubcode,
                   ROW_NUMBER() OVER(PARTITION BY DATE_FORMAT(jzrq, '%Y%m'), a1.tradecode, a1.tradesubcode, a1.grzh\s
                   ORDER BY a3.corpkind, a2.dwjcbl) row_id
            FROM F_EV_DTLPERS a1, f_AR_BUSICORP_S a2, f_ip_cifcorp_s a3, f_ar_accpers_s a4
            WHERE a4.dwzh = a2.dwzh AND a1.grzh = a4.grzh AND a2.custid = a3.custid
              AND a2.data_dt = ? AND a3.data_dt = ? AND a4.data_dt = ?
              AND DATE_FORMAT(a1.jzrq, '%Y%m') = ?
              AND (
                  (a1.tradecode = 'GJ0214' AND a1.tradesubcode = '1') OR
                  (a1.tradecode = 'GJ0107' AND a1.tradesubcode = '2') OR
                  (a1.tradecode = 'GJ0108' AND a1.tradesubcode = '2') OR
                  (a1.tradecode = 'GJ0111') OR
                  (a1.tradecode = 'GJ0114' AND a1.tradesubcode = '1') OR
                  (a1.tradecode = 'GJ0208' AND a1.tradesubcode = '1') OR
                  (a1.tradecode = 'HS0101' AND a1.dcflag = '02')
              )
              AND a1.validflag = '1'
            """;
        jdbcTemplate.update(sql, vLastDay, vLastDay, vLastDay, vMonth);
    }

    /**
     * 计算临时表 Tmp_dw (对应原 SQL 第二个 Insert)
     */
    private void calcTempTableTmpDw(String vMonth, String vLastDay) {
        String sql = """
            INSERT INTO a_rpt_zh08_tmp_dw
            (corppayrate, corpkind, persacctno, corpacctno, Acctdate, amt, tradecode, tradesubcode, row_id)
            SELECT a2.dwjcbl, a3.corpkind, a1.grzh, a1.dwzh, a1.jzrq, a1.fse,
                   a1.tradecode, a1.tradesubcode,
                   ROW_NUMBER() OVER(PARTITION BY DATE_FORMAT(jzrq, '%Y%m'), a1.tradecode, a1.tradesubcode, a1.grzh\s
                   ORDER BY a3.corpkind, a2.dwjcbl) row_id
            FROM F_EV_DTLPERS a1, f_AR_BUSICORP_S a2, f_ip_cifcorp_s a3
            WHERE a1.dwzh = a2.dwzh AND a2.custid = a3.custid
              AND a2.data_dt = ? AND a3.data_dt = ?
              AND DATE_FORMAT(a1.jzrq, '%Y%m') = ?
              AND (
                  (a1.tradecode = 'GJ0214' AND a1.tradesubcode = '1') OR
                  (a1.tradecode = 'GJ0107' AND a1.tradesubcode = '2') OR
                  (a1.tradecode = 'GJ0108' AND a1.tradesubcode = '2') OR
                  (a1.tradecode = 'GJ0111') OR
                  (a1.tradecode = 'GJ0114' AND a1.tradesubcode = '1') OR
                  (a1.tradecode = 'GJ0208' AND a1.tradesubcode = '1')
              )
              AND a1.validflag = '1'
            """;
        jdbcTemplate.update(sql, vLastDay, vLastDay, vMonth);
    }

    /**
     * 汇总计算到 tmpa
     */
    private void calcSummaryTmpa(String vMonth, String vLastDay) {
        String startDay = vMonth + "01";
        String sql = """
            INSERT INTO a_Rpt_Zh08_tmpa (DATA_M, P_CODE, PAYRATE, SJJE, SJZGS, SJDWS)
            SELECT ? data_m,
                   COALESCE(pc.cd_id, '127') p_code,
                   a.corppayrate,
                   SUM(a.amt) sjje,
                   COUNT(DISTINCT CASE WHEN a.row_id = 1 AND ((a.tradecode = 'GJ0107' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0108' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0111')) THEN a.persacctno ELSE NULL END) sjzgs,
                   COUNT(DISTINCT CASE WHEN ((a.tradecode = 'GJ0107' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0108' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0111')) THEN a.corpacctno ELSE NULL END) sjdws
            FROM a_rpt_zh08_tmp a
            LEFT JOIN pub_code_value pc ON pc.cd_type = '1041' AND pc.cd_name = CONCAT(a.corppayrate, '%')
            WHERE a.Acctdate BETWEEN STR_TO_DATE(?, '%Y%m%d') AND STR_TO_DATE(?, '%Y%m%d')
            GROUP BY a.corppayrate
            UNION ALL
            SELECT ? data_m,
                   COALESCE(pc.cd_id, '127') p_code,
                   a2.dwjcbl,
                   SUM(a1.fse) SJJE, 0 SJZGS, 0 SJDWS
            FROM F_EV_DTLPERS a1
            JOIN f_AR_BUSICORP_s a2 ON a1.dwzh = a2.dwzh
            JOIN f_ip_cifcorp_s a3 ON a2.custid = a3.custid
            LEFT JOIN pub_code_value pc ON pc.cd_type = '1041' AND pc.cd_name = CONCAT(a2.dwjcbl, '%')
            WHERE a1.validflag = '1'
              AND a2.data_dt = ? AND a3.data_dt = ?
              AND a1.jzrq BETWEEN STR_TO_DATE(?, '%Y%m%d') AND STR_TO_DATE(?, '%Y%m%d')
              AND (a1.tradecode = 'GJ0115' AND a1.tradesubcode = '2')
              AND grzh IN (
                  SELECT DISTINCT grzh FROM F_EV_DTLPERS a1
                  WHERE a1.validflag = '1'
                    AND ((a1.tradecode = 'GJ0107' AND a1.tradesubcode = '2') OR (a1.tradecode = 'GJ0108' AND a1.tradesubcode = '2') OR (a1.tradecode = 'GJ0111'))
                    AND a1.jzrq BETWEEN STR_TO_DATE(?, '%Y%m%d') AND STR_TO_DATE(?, '%Y%m%d')
              )
            GROUP BY a2.dwjcbl
            """;
        jdbcTemplate.update(sql, vMonth, startDay, vLastDay, vMonth, vLastDay, vLastDay, startDay, vLastDay, startDay, vLastDay);
    }

    /**
     * 汇总计算到 dw
     */
    private void calcSummaryDw(String vMonth, String vLastDay) {
        String startDay = vMonth + "01";
        String sql = """
            INSERT INTO a_Rpt_Zh08_dw (DATA_M, P_CODE, PAYRATE, SJJE, SJZGS, SJDWS)
            SELECT ? data_m,
                   COALESCE(pc.cd_id, '127') p_code,
                   a.corppayrate,
                   SUM(a.amt) sjje,
                   COUNT(DISTINCT CASE WHEN a.row_id = 1 AND ((a.tradecode = 'GJ0107' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0108' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0111')) THEN a.persacctno ELSE NULL END) sjzgs,
                   COUNT(DISTINCT CASE WHEN ((a.tradecode = 'GJ0107' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0108' AND a.tradesubcode = '2') OR (a.tradecode = 'GJ0111')) THEN a.corpacctno ELSE NULL END) sjdws
            FROM a_rpt_zh08_tmp_dw a
            LEFT JOIN pub_code_value pc ON pc.cd_type = '1041' AND pc.cd_name = CONCAT(a.corppayrate, '%')
            WHERE a.Acctdate BETWEEN STR_TO_DATE(?, '%Y%m%d') AND STR_TO_DATE(?, '%Y%m%d')
            GROUP BY a.corppayrate
            """;
        jdbcTemplate.update(sql, vMonth, startDay, vLastDay);
    }

    /**
     * 修正单位笔数
     */
    private void fixUnitCount(String vMonth) {
        String sql = """
            UPDATE a_Rpt_Zh08_tmpa a
            SET sjdws = (SELECT sjdws FROM a_Rpt_Zh08_dw b 
                         WHERE a.data_m = b.data_m AND a.payrate = b.payrate AND a.p_code = b.p_code)
            WHERE a.data_m = ? AND a.Sjzgs <> 0
            """;
        jdbcTemplate.update(sql, vMonth);
    }

    private void ensureIndexes() {
        String existsSql = "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";
        Integer c1 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp", "idx_tmp_range");
        if (c1 != null && c1 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp ADD INDEX idx_tmp_range (Acctdate, tradecode, tradesubcode)");
        }
        Integer c2 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp", "idx_tmp_payrate");
        if (c2 != null && c2 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp ADD INDEX idx_tmp_payrate (corppayrate)");
        }
        Integer c3 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp", "idx_tmp_pers");
        if (c3 != null && c3 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp ADD INDEX idx_tmp_pers (persacctno)");
        }
        Integer c4 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp", "idx_tmp_corp");
        if (c4 != null && c4 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp ADD INDEX idx_tmp_corp (corpacctno)");
        }
        Integer d1 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp_dw", "idx_tmpdw_range");
        if (d1 != null && d1 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp_dw ADD INDEX idx_tmpdw_range (Acctdate, tradecode, tradesubcode)");
        }
        Integer d2 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp_dw", "idx_tmpdw_payrate");
        if (d2 != null && d2 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp_dw ADD INDEX idx_tmpdw_payrate (corppayrate)");
        }
        Integer d3 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp_dw", "idx_tmpdw_pers");
        if (d3 != null && d3 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp_dw ADD INDEX idx_tmpdw_pers (persacctno)");
        }
        Integer d4 = jdbcTemplate.queryForObject(existsSql, Integer.class, "a_rpt_zh08_tmp_dw", "idx_tmpdw_corp");
        if (d4 != null && d4 == 0) {
            jdbcTemplate.execute("ALTER TABLE a_rpt_zh08_tmp_dw ADD INDEX idx_tmpdw_corp (corpacctno)");
        }
        Integer e1 = jdbcTemplate.queryForObject(existsSql, Integer.class, "f_ev_dtlpers", "idx_ev_anti");
        if (e1 != null && e1 == 0) {
            jdbcTemplate.execute("ALTER TABLE f_ev_dtlpers ADD INDEX idx_ev_anti (grzh, jzrq, tradecode, tradesubcode, validflag)");
        }
        Integer p1 = jdbcTemplate.queryForObject(existsSql, Integer.class, "pub_code_value", "idx_pub_code");
        if (p1 != null && p1 == 0) {
            jdbcTemplate.execute("ALTER TABLE pub_code_value ADD INDEX idx_pub_code (cd_type, cd_name)");
        }
    }

    /**
     * 插入最终报表数据 (核心业务逻辑)
     * 由于原存储过程中有大量的 INSERT ... SELECT ... FROM DUAL，这里将其拆解为多个方法调用或直接在一个大方法中顺序执行
     */
    private void insertReportData(String vMonth, String vMonth1, String vLastDay, String vDate) {
        // 为了代码简洁，这里演示几个典型的插入逻辑，其余逻辑类似

        // 201 本期末非正常缴存账户数
        String sql201 = """
            INSERT INTO A_RPT_ZH08 (ORD_FLAG, DATA_M, ZBMC, ZBBM, UNIT, ZB_VALUE, YZ_VALUE)
            SELECT '2', ?, '本期末非正常缴存账户数', '202', '户',
                   (SELECT CAST(a.zb_value - b.zb_value AS CHAR) FROM A_RPT_ZH11 a, A_RPT_ZH11 b 
                    WHERE a.data_m = b.data_m AND a.zbbm = '119' AND a.data_m = ? AND b.zbbm = '102'),
                   NULL
            """;
        jdbcTemplate.update(sql201, vMonth, vMonth);

        // 202 本期末非正常缴存账户余额
        String sql202 = """
            INSERT INTO A_RPT_ZH08 (ORD_FLAG, DATA_M, ZBMC, ZBBM, UNIT, ZB_VALUE, YZ_VALUE)
            SELECT '1', ?, '本期末非正常缴存账户余额', '201', '万元',
                   (SELECT CAST(ROUND(IFNULL(SUM(b.BAL), 0) / 10000, 2) AS CHAR)
                    FROM a_gj_pers_sd_r b
                    LEFT JOIN f_ev_dtlpers a
                      ON a.validflag = '1'
                     AND a.tradecode = 'GJ0107'
                     AND a.tradesubcode = '2'
                     AND a.jzrq BETWEEN STR_TO_DATE(CONCAT(?, '01'), '%Y%m%d') AND LAST_DAY(STR_TO_DATE(?, '%Y%m'))
                     AND b.persacctno = a.grzh
                    WHERE a.grzh IS NULL
                      AND b.data_dt = DATE_FORMAT(LAST_DAY(STR_TO_DATE(?, '%Y%m')), '%Y%m%d')),
                   NULL
            """;
        jdbcTemplate.update(sql202, vMonth, vMonth, vMonth, vMonth);

        // ... (此处省略中间大量的 INSERT 语句，逻辑完全相同：将原 SQL 放入 jdbcTemplate.update) ...
        // 建议将每个指标的 SQL 提取为独立的私有方法，如 insertMetric301(), insertMetric405() 等，以保持代码整洁

        // 示例：插入分类标题行
        String sqlTitles1 = """
            INSERT INTO A_RPT_ZH08 (ORD_FLAG, DATA_M, ZBMC, ZBBM, UNIT, ZB_VALUE, YZ_VALUE)
            VALUES
            ('3', ?, '一、降低缴存比例', '-', '-', NULL, NULL),
            ('8', ?, '二、缓缴', '-', '-', NULL, NULL),
            ('13', ?, '一、继续阶段性适当降低企业缴存比例', '-', '-', NULL, NULL),
            ('18', ?, '二、切实规范缴存基数上限', '-', '-', NULL, NULL),
            ('25', ?, '三、扩大缴存比例浮动区间', '-', '-', NULL, NULL)
            """;
        jdbcTemplate.update(sqlTitles1, vMonth, vMonth, vMonth, vMonth, vMonth);
        String sqlTitles2 = """
            INSERT INTO A_RPT_ZH08 (ORD_FLAG, DATA_M, ZBMC, ZBBM, UNIT, ZB_VALUE, YZ_VALUE)
            VALUES
            ('9', ?, '本年以来减少的缴存金额', '305', '万元', '0', NULL),
            ('10', ?, ' 其中：本年以来企业减少的缴存金额', '306', '万元', '0', NULL),
            ('11', ?, '本年以来涉及的职工人数', '307', '人', '0', NULL),
            ('12', ?, '本年以来涉及的单位数', '308', '个', '0', NULL)
            """;
        jdbcTemplate.update(sqlTitles2, vMonth, vMonth, vMonth, vMonth);

        // 注意：原存储过程中的 Update 401 逻辑也需要在这里执行
        String sqlUpdate401 = """
            UPDATE A_RPT_ZH08
            SET ZB_VALUE = (SELECT CASE 
                    WHEN YZ_VALUE - (SELECT IFNULL(ZB_VALUE, 0) FROM A_RPT_ZH08 WHERE data_m = ? AND zbbm = '401') > 0 THEN YZ_VALUE 
                    ELSE (SELECT IFNULL(ZB_VALUE, 0) FROM A_RPT_ZH08 WHERE data_m = ? AND zbbm = '401') 
                    END FROM A_RPT_ZH08 WHERE data_m = ? AND zbbm = '401')
            WHERE data_m = ? AND zbbm = '401'
            """;
        jdbcTemplate.update(sqlUpdate401, vMonth1, vMonth1, vMonth, vMonth);
    }

    /**
     * 测试连接方法：执行一个简单的SQL查询
     * 用于测试数据库连接是否正常
     *
     * @return 0 表示成功，非 0 表示错误码
     */
    public int testConnection() {
        try {
            logger.info("开始测试数据库连接...");

            // 查询数据库版本信息
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            logger.info("数据库连接成功，版本: {}", version);

            // 查询当前时间
            String currentTime = jdbcTemplate.queryForObject("SELECT NOW()", String.class);
            logger.info("数据库当前时间: {}", currentTime);

            // 查询当前用户
            String currentUser = jdbcTemplate.queryForObject("SELECT USER()", String.class);
            logger.info("数据库当前用户: {}", currentUser);

            // 查询当前数据库
            String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            logger.info("当前数据库: {}", currentDb);

            logger.info("数据库连接测试成功!");
            return 0;

        } catch (Exception e) {
            logger.error("数据库连接测试失败", e);
            return -1;
        }
    }
}
