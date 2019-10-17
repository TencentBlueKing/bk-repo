package com.tencent.bkrepo.docker.md

import com.tencent.bkrepo.docker.common.Info
import kotlin.collections.Map.Entry

interface PropertiesInfo : Info {

    val isEmpty: Boolean

    fun size(): Int

    operator fun get(key: String): Set<String>?

    fun values(): Collection<String>

    fun entries(): Set<Entry<String, String>>

    fun keySet(): Set<String>

    fun containsKey(key: String): Boolean

    fun getFirst(key: String): String?

    companion object {
        val ROOT = "properties"
    }
}
