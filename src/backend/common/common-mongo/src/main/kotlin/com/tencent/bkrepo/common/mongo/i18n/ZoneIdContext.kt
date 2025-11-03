package com.tencent.bkrepo.common.mongo.i18n

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.ZoneId
import java.time.ZoneOffset

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
        
        return try {
            // 尝试直接解析为标准时区ID
            ZoneId.of(zoneStr)
        } catch (_: Exception) {
            try {
                // 尝试解析为UTC偏移量
                if (zoneStr.startsWith("+") || zoneStr.startsWith("-") || zoneStr == "Z") {
                    ZoneOffset.of(zoneStr)
                } else {
                    // 尝试解析为小时偏移（如"+8"或"-5"）
                    val hours = zoneStr.toIntOrNull()
                    if (hours != null) {
                        ZoneOffset.ofHours(hours)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

