package com.tencent.bkrepo.common.query.enums

import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 排序类型
 *
 * @author: carrypan
 * @date: 2019/11/14
 */
enum class OperationType(val valueType: KClass<*>) {
    EQ(Any::class),
    NE(Any::class),
    LTE(Number::class),
    LT(Number::class),
    GTE(Number::class),
    GT(Number::class),
    BEFORE(LocalDateTime::class),
    AFTER(LocalDateTime::class),
    IN(List::class),
    PREFIX(String::class),
    SUFFIX(String::class),
    NULL(Void::class),
    NOT_NULL(Void::class);

    companion object {
        val DEFAULT = EQ

        fun lookup(value: String): OperationType {
            val upperCase = value.toUpperCase()
            return values().find { it.name == upperCase } ?: DEFAULT
        }
    }
}
