package com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration

/**
 * RPM仓库个性化属性: repodata_depth  索引目录深度
 */
class RpmLocalConfiguration(
        val repodata_depth: Int? = 0
) : LocalConfiguration() {
    companion object {
        const val type = "rpm-local"
    }
}