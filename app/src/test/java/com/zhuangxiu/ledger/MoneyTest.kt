package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 金额格式化与解析 */
class MoneyTest {

    /** 格式化为人民币符号加千分位 */
    @Test
    fun formatWithYuanSymbolAndThousandsSeparator() {
        assertEquals("¥0.00", Money.format(0))
        assertEquals("¥0.05", Money.format(5))
        assertEquals("¥12.50", Money.format(1250))
        assertEquals("¥1,234.56", Money.format(123456))
        assertEquals("¥1,000,000.00", Money.format(100_000_000))
        assertEquals("¥999.99", Money.format(99999))
        assertEquals("-¥1,234.56", Money.format(-123456))
        assertEquals("1,234.56", Money.format(123456, withSymbol = false))
    }

    /** 把输入的元解析成分且没有浮点误差 */
    @Test
    fun parseYuanToCentsWithoutFloatError() {
        assertEquals(1250L, Money.parseToCents("12.5"))
        assertEquals(1999L, Money.parseToCents("19.99"))
        assertEquals(10L, Money.parseToCents("0.1"))
        assertEquals(123456789L, Money.parseToCents("1,234,567.89"))
        assertEquals(500L, Money.parseToCents(" 5 "))
        assertEquals(0L, Money.parseToCents("0"))
    }

    /** 非法输入返回null */
    @Test
    fun invalidInputReturnsNull() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("-5"))
        assertNull(Money.parseToCents("1.234"))
    }

    /** 分转回输入框文本 */
    @Test
    fun centsToInputText() {
        assertEquals("", Money.toInput(0))
        assertEquals("12.5", Money.toInput(1250))
        assertEquals("100", Money.toInput(10000))
        assertEquals("0.05", Money.toInput(5))
    }

    /** 输入过滤只保留数字和两位小数 */
    @Test
    fun sanitizeKeepsDigitsAndTwoDecimals() {
        assertEquals("12.34", Money.sanitizeInput("12.345"))
        assertEquals("0.5", Money.sanitizeInput(".5"))
        assertEquals("1.23", Money.sanitizeInput("1.2.3"))
        assertEquals("123", Money.sanitizeInput("1a2b3"))
    }

    @Test
    fun oversizedInputIsRejectedWithoutChangingItsLeadingDigits() {
        assertEquals("1234567890.12", Money.sanitizeInput("1234567890.12"))
        assertNull(Money.parseToCents("1234567890.12"))
        assertEquals(99999999999L, Money.parseToCents("999999999.99"))
    }

    /** 图表用的简短金额 */
    @Test
    fun shortFormatForCharts() {
        assertEquals("¥500", Money.formatShort(50000))
        assertEquals("¥1.2万", Money.formatShort(1_200_000))
        assertEquals("¥10万", Money.formatShort(10_000_000))
    }
}
