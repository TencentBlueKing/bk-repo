package com.tencent.bkrepo.npm.pojo.migration

data class MigrationFailDataDetailInfo (
    val pkgName: String,
    val versionSet: MutableSet<VersionFailDetail>
)

data class VersionFailDetail(
    val version: String,
    val reason: String?
)