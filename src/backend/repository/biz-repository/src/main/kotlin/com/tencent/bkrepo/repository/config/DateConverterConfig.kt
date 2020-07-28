package com.tencent.bkrepo.repository.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Configuration
class DateConverterConfig {
    /**
     * LocalDate转换器，用于转换RequestParam和PathVariable参数
     */
    @Bean
    fun localDateConverter(): Converter<String, LocalDate> {
        return Converter { source -> LocalDate.parse(source, DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)) }
    }

    companion object {
        private const val DEFAULT_DATE_FORMAT = "yyyy-MM-dd"
    }
}
