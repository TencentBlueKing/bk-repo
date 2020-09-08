package com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration

/**
 * RPM仓库个性化属性: repodataDepth  索引目录深度
 *                  enabledFileLists  是否启用filelsit
 */
class RpmLocalConfiguration(
    val repodataDepth: Int,
    val enabledFileLists: Boolean
) : LocalConfiguration() {
    companion object {
        const val type = "rpm-local"
    }
}
