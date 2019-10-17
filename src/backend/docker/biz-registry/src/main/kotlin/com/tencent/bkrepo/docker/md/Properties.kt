package com.tencent.bkrepo.docker.md

import com.google.common.collect.Multimap
import com.google.common.collect.Multiset

interface Properties : MutablePropertiesInfo {

    fun putAll(multimap: Multimap<out String, out String>): Boolean

    fun putAll(map: Map<out String, String>): Boolean

    fun putAll(properties: PropertiesInfo): Boolean

    fun keys(): Multiset<String>

    fun hasMandatoryProperty(): Boolean

    fun matchQuery(queryProperties: Properties): Properties.MatchResult

    enum class MatchResult private constructor() {
        MATCH,
        NO_MATCH,
        CONFLICT
    }

    companion object {
        val MATRIX_PARAMS_SEP = ";"
        val MANDATORY_SUFFIX = "+"
    }
}
