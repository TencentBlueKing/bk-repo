package com.tencent.bkrepo.common.mongo.i18n

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
     * - 小时偏移: "+8", "-5"
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
     * 解析小时偏移（如 "+8", "-5"）
     */
    private fun parseHourOffset(zoneStr: String): ZoneOffset? {
        val hours = zoneStr.toIntOrNull() ?: return null
        return try {
            ZoneOffset.ofHours(hours)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 根据时区格式化 LocalDateTime
     * 将 LocalDateTime（视为 sourceZoneId 时区的本地时间）转换为 targetZoneId 时区的本地时间
     * 输出不带时区偏移的 ISO 格式字符串
     *
     */
    fun LocalDateTime.zoneFormat(formatter: DateTimeFormatter): String{
        val sourceZoneId = getDefaultZoneId()
        val targetZoneId = getZoneIdOrNull() ?: sourceZoneId
        if (sourceZoneId.equals(targetZoneId)) {
            return format(formatter)
        }
        // 将 LocalDateTime 视为 sourceZoneId 时区的本地时间，转换为 Instant
        val instant = atZone(sourceZoneId).toInstant()
        // 转换为目标时区的本地时间
        val targetLocalDateTime = instant.atZone(targetZoneId).toLocalDateTime()
        // 格式化为不带时区偏移的 ISO 格式
        return targetLocalDateTime.format(formatter)
    }

}

