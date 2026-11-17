package com.zhuangxiu.ledger.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * 金额工具。全应用金额都用 Long 按“分”存储，
 * 展示时统一格式化为“¥ + 千分位 + 两位小数”，例如 ¥12,345.60。
 */
object Money {

    /** 分 -> ¥1,234.56 */
    fun format(cents: Long, withSymbol: Boolean = true): String {
        val absCents = abs(cents)
        val yuan = absCents / 100
        val fen = absCents % 100
        // 手动插入千分位，不依赖系统语言环境
        val digits = yuan.toString()
        val grouped = StringBuilder()
        for ((index, ch) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) grouped.append(',')
            grouped.append(ch)
        }
        return buildString {
            if (cents < 0) append('-')
            if (withSymbol) append('¥')
            append(grouped)
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }

    /** 大数字的简短写法，用于图表坐标：¥1.2万 */
    fun formatShort(cents: Long): String {
        val yuan = cents / 100.0
        return when {
            yuan >= 10_000 -> "¥" + BigDecimal(yuan / 10_000).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "万"
            else -> "¥" + BigDecimal(yuan).setScale(0, RoundingMode.HALF_UP).toPlainString()
        }
    }

    /**
     * 把用户输入的金额文本（元）解析成分。
     * 输入非法、为负数、小数超过两位或超过 999,999,999.99 元时返回 null。
     */
    fun parseToCents(input: String): Long? {
        val text = input.trim().replace(",", "")
        if (text.isEmpty()) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value.scale() > 2 || value > BigDecimal("999999999.99")) return null
        return try {
            value.movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** 分 -> 输入框里的文本（元），去掉多余的 0，例如 1250 -> "12.5" */
    fun toInput(cents: Long): String =
        if (cents == 0L) "" else BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

    /** 输入过程中的过滤：只保留数字和一个小数点，小数最多两位 */
    fun sanitizeInput(raw: String): String {
        val sb = StringBuilder()
        var dotSeen = false
        var decimals = 0
        for (ch in raw) {
            when {
                ch.isDigit() -> {
                    if (dotSeen) {
                        if (decimals >= 2) continue
                        decimals++
                    }
                    sb.append(ch)
                }
                (ch == '.' || ch == '。') && !dotSeen -> {
                    dotSeen = true
                    if (sb.isEmpty()) sb.append('0')
                    sb.append('.')
                }
            }
        }
        // 保留用户输入，超出业务上限由 parseToCents 拒绝，不能截掉高位变成另一笔金额。
        return sb.toString()
    }
}
