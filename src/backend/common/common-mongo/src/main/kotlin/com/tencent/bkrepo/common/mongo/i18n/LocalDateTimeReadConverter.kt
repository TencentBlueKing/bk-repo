package com.tencent.bkrepo.common.mongo.i18n

import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import java.time.LocalDateTime
import java.util.Date

/**
 * LocalDateTime读取转换器
 * 从数据库读取Date类型时，转换为指定时区的LocalDateTime
 * 优先使用ZoneIdContext中的时区（从请求头获取），如果没有则使用构造时指定的默认时区
 */
@ReadingConverter
class LocalDateTimeReadConverter() : Converter<Date, LocalDateTime> {

    override fun convert(source: Date): LocalDateTime {
        // 优先使用上下文中的时区（从请求头获取）
        // 如果上下文没有设置，则使用 ZoneIdContext 的默认时区
        val contextZoneId = ZoneIdContext.getZoneIdOrNull()
        val defaultZoneId = ZoneIdContext.getDefaultZoneId()
        val zoneId = contextZoneId ?: defaultZoneId
        
        val result = source.toInstant()
            .atZone(zoneId)
            .toLocalDateTime()
        
        if (logger.isDebugEnabled) {
            logger.debug(
                "[LocalDateTimeReadConverter] Converting Date to LocalDateTime: " +
                        "source=$source, contextZoneId=${contextZoneId?.id}, defaultZoneId=${defaultZoneId.id}, " +
                        "finalZoneId=${zoneId.id}, result=$result",
            )
        }

        return result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LocalDateTimeReadConverter::class.java)
    }
}

