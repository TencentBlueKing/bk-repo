package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.RpmVersion

object RpmVersionUtils {
    fun RpmVersion.toMetadata(): MutableMap<String, String> {
        return mutableMapOf(
            "name" to this.name,
            "arch" to this.arch,
            "epoch" to this.epoch,
            "ver" to this.ver,
            "rel" to this.rel
        )
    }
}
