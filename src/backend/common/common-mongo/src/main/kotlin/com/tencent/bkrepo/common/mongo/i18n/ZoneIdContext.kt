package com.tencent.bkrepo.common.mongo.i18n

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 时区上下文
 * 支持从请求头中动态获取时区
 */
object ZoneIdContext {
    
    /**
     * 时区请求头名称（优先级高）
     */
    const val TIME_ZONE_HEADER = "X-BKREPO-Time-Zone"
    
    /**
     * 默认时区（当请求头中没有指定时区时使用）
     */
    private var defaultZoneId: ZoneId = ZoneId.systemDefault()
    
    /**
     * 获取当前请求的时区，优先从请求头获取，如果没有则返回默认时区
     */
    fun getZoneId(): ZoneId {
        return getZoneIdOrNull() ?: defaultZoneId
    }
    
    /**
     * 获取当前请求的时区（可能为null）
     * 直接从请求头中读取，不缓存
     */
    fun getZoneIdOrNull(): ZoneId? {
        val request = getRequestOrNull() ?: return null
        val zoneIdStr = request.getHeader(TIME_ZONE_HEADER)

        if (!zoneIdStr.isNullOrBlank()) {
            return parseZoneId(zoneIdStr)
        }
        return null
    }
    
    /**
     * 获取HttpServletRequest（可能为null）
     */
    private fun getRequestOrNull(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() ?: return null
        return if (requestAttributes is ServletRequestAttributes) {
            requestAttributes.request
        } else {
            null
        }
    }
    
    /**
     * 设置默认时区
     */
    fun setDefaultZoneId(zoneId: ZoneId) {
        defaultZoneId = zoneId
    }
    
    /**
     * 获取默认时区
     */
    fun getDefaultZoneId(): ZoneId {
        return defaultZoneId
    }
    
    /**
     * 根据字符串解析时区
     * 支持的格式：
     * - 标准时区ID: "Asia/Shanghai", "America/New_York", "UTC"
     * - UTC偏移量: "+08:00", "-05:00", "Z"
     * - 小时偏移: "+8", "-5", "+8.5", "-5.5", "8", "8.5"（无符号时默认当作 + 号处理）
     */
    fun parseZoneId(zoneStr: String?): ZoneId? {
        if (zoneStr.isNullOrBlank()) {
            return null
        }
        
        // 优先尝试解析为标准时区ID
        parseStandardZoneId(zoneStr)?.let { return it }
        
        // 尝试解析为UTC偏移量
        parseZoneOffset(zoneStr)?.let { return it }
        
        // 尝试解析为小时偏移
        parseHourOffset(zoneStr)?.let { return it }
        
        return null
    }
    
    /**
     * 解析标准时区ID（如 "Asia/Shanghai", "UTC"）
     */
    private fun parseStandardZoneId(zoneStr: String): ZoneId? {
        return try {
            ZoneId.of(zoneStr)
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * 解析UTC偏移量（如 "+08:00", "-05:00", "Z"）
     */
    private fun parseZoneOffset(zoneStr: String): ZoneOffset? {
        if (!isZoneOffsetFormat(zoneStr)) {
            return null
        }
        return try {
            ZoneOffset.of(zoneStr)
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * 判断是否为UTC偏移量格式
     */
    private fun isZoneOffsetFormat(zoneStr: String): Boolean {
        return zoneStr == "Z" || zoneStr.startsWith("+") || zoneStr.startsWith("-")
    }
    
    /**
     * 解析小时偏移（如 "+8", "-5", "+8.5", "-5.5", "8", "8.5"）
     * 如果没有 + 或 - 号，默认当作 + 号处理
     */
    private fun parseHourOffset(zoneStr: String): ZoneOffset? {
        val (sign, numberStr) = when {
            zoneStr.startsWith("+") -> 1 to zoneStr.substring(1)
            zoneStr.startsWith("-") -> -1 to zoneStr.substring(1)
            else -> 1 to zoneStr  // 没有符号时，默认当作 + 号处理
        }
        
        val hours = numberStr.toDoubleOrNull() ?: return null
        val totalSeconds = (sign * hours * 3600).toInt()
        
        return try {
            ZoneOffset.ofTotalSeconds(totalSeconds)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 根据时区解析字符串为 LocalDateTime
     * 将字符串（视为 sourceZoneId 时区的本地时间）转换为 targetZoneId 时区的本地时间
     * 输入不带时区偏移的字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @param formatter 日期时间格式化器
     * @return 解析后的 LocalDateTime（targetZoneId 时区的本地时间）
     */
    fun zoneParse(dateTimeStr: CharSequence, formatter: DateTimeFormatter): LocalDateTime {
        val targetZoneId = getDefaultZoneId()
        val sourceZoneId = getZoneId()
        // 将字符串解析为 LocalDateTime（视为目标时区的本地时间）
        val sourceLocalDateTime = LocalDateTime.parse(dateTimeStr, formatter)
        if (sourceZoneId.equals(targetZoneId)) {
            return sourceLocalDateTime
        }
        // 将源时区的 LocalDateTime 转换为 Instant
        val instant = sourceLocalDateTime.atZone(sourceZoneId).toInstant()
        // 转换为服务端时区的本地时间
        return instant.atZone(targetZoneId).toLocalDateTime()
    }

    /**
     * 将 ZoneId 格式化为 +HHMM 格式的字符串
     * 例如：Asia/Shanghai -> +0800, UTC -> +0000, America/New_York -> -0500
     * 支持任意分钟数，如 +0850（8小时50分钟）、+0530（5小时30分钟）
     *
     * @param zoneId 时区ID
     * @return 格式化的时区字符串，格式为 +HHMM 或 -HHMM
     */
    fun formatZoneId(zoneId: ZoneId): String {
        val offset = zoneId.rules.getOffset(java.time.Instant.now())
        val totalSeconds = offset.totalSeconds
        val hours = totalSeconds / 3600
        val minutes = abs((totalSeconds % 3600) / 60)
        return String.format("%+03d%02d", hours, minutes)
    }

    /**
     * 获取当前时区并格式化为 +HHMM 格式的字符串
     * 支持任意分钟数，如 +0850（8小时50分钟）、+0530（5小时30分钟）
     *
     * @return 格式化的时区字符串，格式为 +HHMM 或 -HHMM
     */
    fun getZoneIdAsOffsetString(): String {
        return formatZoneId(getZoneId())
    }

}

