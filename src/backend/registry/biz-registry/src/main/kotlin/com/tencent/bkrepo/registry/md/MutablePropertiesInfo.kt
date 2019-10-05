package com.tencent.bkrepo.registry.md

interface MutablePropertiesInfo : PropertiesInfo {
    fun putAll(key: String?, values: Iterable<String>): Boolean

    fun putAll(key: String?, values: Array<String>): Boolean

    fun replaceValues(key: String, values: Iterable<String>): Set<String>?

    fun clear()

    fun removeAll(key: Any?): Set<String>

    fun put(key: String, value: String): Boolean
}
