package com.tencent.bkrepo.helm.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Configuration
class DateConverterConfig {
    /**
     * LocalDateTime转换器，用于转换RequestParam和PathVariable参数
     */
    @Bean
    fun localDateTimeConverter(): Converter<String, LocalDateTime> {
        return Converter {
            source ->
            LocalDateTime.parse(source, DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT))
        }
    }

    @Bean
    fun localDateConverter(): Converter<String, LocalDate> {
        return Converter {
            source ->
            LocalDate.parse(source, DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT))
        }
    }

    companion object {
        private const val DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val DEFAULT_DATE_FORMAT = "yyyy-MM-dd"
    }
}
