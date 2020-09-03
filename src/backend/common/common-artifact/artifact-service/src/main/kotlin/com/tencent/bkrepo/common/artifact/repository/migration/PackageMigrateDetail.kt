package com.tencent.bkrepo.common.artifact.repository.migration

/**
 * 包迁移详情信息
 */
data class PackageMigrateDetail(
    /**
     * 包名
     */
    val packageName: String,

    /**
     * 版本迁移成功集合，记录名称
     */
    val successVersionList: MutableSet<String> = mutableSetOf(),

    /**
     * 版本迁移失败集合，记录名称和错误原因
     */
    val failureVersionDetailList: MutableSet<VersionMigrateErrorDetail> = mutableSetOf()
) {
    /**
     * 获取总的迁移包数量
     */
    fun getVersionCount(): Int = successVersionList.size + failureVersionDetailList.size

    /**
     * 添加版本[version]到成功列表
     */
    fun addSuccessVersion(version: String) {
        this.successVersionList.add(version)
    }

    /**
     * 添加版本[version]到失败列表，[reason]为失败原因
     */
    fun addFailureVersion(version: String, reason: String) {
        this.failureVersionDetailList.add(VersionMigrateErrorDetail(version, reason))
    }
}