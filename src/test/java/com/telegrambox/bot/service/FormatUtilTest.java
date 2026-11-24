package com.telegrambox.bot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 金额格式化：最多 4 位小数，去尾零；返佣保留 2 位小数。 */
class FormatUtilTest {

    @Test
    void fmtMoneyMatchesScreenshot() {
        assertEquals("8.9983", FormatUtil.fmtMoney(8.9983));
        assertEquals("11.502", FormatUtil.fmtMoney(11.502));
        assertEquals("88.498", FormatUtil.fmtMoney(88.498));
        assertEquals("0", FormatUtil.fmtMoney(0));
    }

    @Test
    void fmtMoneyStripsTrailingZeros() {
        assertEquals("20", FormatUtil.fmtMoney(20.0));
        assertEquals("19.99", FormatUtil.fmtMoney(19.99));
        assertEquals("10.5", FormatUtil.fmtMoney(10.5));
        assertEquals("0.1", FormatUtil.fmtMoney(0.1));
    }

    @Test
    void fmtMoneyRoundsToFourDecimals() {
        assertEquals("1.2346", FormatUtil.fmtMoney(1.23456));
        assertEquals("1.2345", FormatUtil.fmtMoney(1.23454));
    }

    @Test
    void fmtUsd() {
        assertEquals("$8.9983", FormatUtil.fmtUsd(8.9983));
        assertEquals("$0", FormatUtil.fmtUsd(0));
    }

    @Test
    void commissionFivePercent() {
        assertEquals(1.0, FormatUtil.commissionFor(20.0, 0.05), 1e-9);
        assertEquals(0.6, FormatUtil.commissionFor(12.0, 0.05), 1e-9);
        assertEquals(0.5, FormatUtil.commissionFor(10.0, 0.05), 1e-9);
        assertEquals(1.0, FormatUtil.commissionFor(19.99, 0.05), 1e-9); // 0.9995 -> 1.00
    }

    @Test
    void round2() {
        assertEquals(0.3, FormatUtil.round2(0.30004), 1e-9);
        assertEquals(0.31, FormatUtil.round2(0.305), 1e-9);
    }
}
