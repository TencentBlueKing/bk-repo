package com.tencent.bkrepo.rpm.pojo

data class RpmRepoConf(
    val repodataDepth: Int,
    val enabledFileLists: Boolean,
    val groupXmlSet: MutableList<String>
)
