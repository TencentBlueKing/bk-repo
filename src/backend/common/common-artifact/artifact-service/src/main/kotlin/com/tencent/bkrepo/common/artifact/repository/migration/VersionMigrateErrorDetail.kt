package com.tencent.bkrepo.common.artifact.repository.migration

/**
 * 版本迁移失详情
 */
data class VersionMigrateErrorDetail(
    /**
     * 版本号
     */
    val version: String,
    /**
     * 失败原因
     */
    val reason: String
)
