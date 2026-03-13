package com.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class RptZh08ServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;
    private RptZh08Service rptService;

    @BeforeEach
    public void setup() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        transactionManager = Mockito.mock(DataSourceTransactionManager.class);
        rptService = new RptZh08Service(jdbcTemplate, transactionManager);

        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(Mockito.any(TransactionDefinition.class))).thenReturn(status);
        doNothing().when(transactionManager).commit(Mockito.any(TransactionStatus.class));
        doNothing().when(transactionManager).rollback(Mockito.any(TransactionStatus.class));

        when(jdbcTemplate.update(Mockito.anyString(), Mockito.<Object[]>any())).thenReturn(1);
        doNothing().when(jdbcTemplate).execute(anyString());
    }

    @Test
    public void testExecuteProc() {
        String inputDate = "20231031";
        int result = rptService.executeProc(inputDate);
        assertEquals(0, result);
    }

    @Test
    public void testExecuteProcWithF() {
        String inputDate = "20231031";
        int result = rptService.f(inputDate);
        assertEquals(0, result);
    }

    @Test
    public void testExecuteProcWithInvalidDate() {
        String invalidDate = "2023-10-31";
        int result = rptService.executeProc(invalidDate);
        assertEquals(-1, result);
    }
}
