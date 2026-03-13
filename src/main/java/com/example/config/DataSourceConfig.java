package com.example.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 数据源配置类
 * 从 db-config.properties 加载数据库配置
 */
@Configuration
@PropertySource("classpath:db-config.properties")
@EnableTransactionManagement
public class DataSourceConfig {

    @Value("${db.url}")
    private String dbUrl;

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    @Value("${db.driver-class-name:oracle.jdbc.OracleDriver}")
    private String driverClassName;

    @Value("${db.pool.max-size:10}")
    private int maxPoolSize;

    @Value("${db.pool.min-idle:2}")
    private int minIdle;

    @Value("${db.pool.connection-timeout:30000}")
    private long connectionTimeout;

    /**
     * 配置数据源
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName(driverClassName);

        // 连接池配置
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setPoolName("HikariPool-RptZh08");

        // 连接测试 (MySQL)
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    /**
     * 配置 JdbcTemplate
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 配置事务管理器
     */
    @Bean
    @Primary
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
