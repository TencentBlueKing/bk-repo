package com.tencent.bkrepo.common.notify.pojo.enums

import com.fasterxml.jackson.annotation.JsonValue

enum class EnumEmailFormat(private val format: Int) {
    PLAIN_TEXT(0),
    HTML(1);

    @JsonValue
    fun getValue(): Int {
        return format
    }

    companion object {
        fun parse(value: Int?): EnumEmailFormat {
            return values().find { it.getValue() == value } ?: PLAIN_TEXT
        }
    }
}
