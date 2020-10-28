package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.rpm.pojo.RpmRepoConf

object RpmConfiguration {
    fun RepositoryConfiguration.toRpmRepoConf(): RpmRepoConf {
        val repodataDepth = this.getIntegerSetting("repodataDepth") ?: 0
        val enabledFileLists = this.getBooleanSetting("enabledFileLists") ?: false
        val groupXmlSet = this.getSetting<MutableList<String>>("groupXmlSet") ?: mutableListOf()
        return RpmRepoConf(repodataDepth, enabledFileLists, groupXmlSet)
    }
}
