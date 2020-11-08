package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.repository.pojo.node.NodeInfo

object RpmCollectionUtils {
    /**
     * 检查repodata 目录是否契合深度
     */
    fun filterByDepth(repodataList: List<String>, depth: Int): List<String> {
        return repodataList.filter {
            it.removePrefix("/").removeSuffix("/").split("/").size == (depth.inc())
        }
    }
}
