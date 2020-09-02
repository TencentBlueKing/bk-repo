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
    val successSet: List<String>,

    /**
     * 版本迁移失败集合，记录名称和错误原因
     */
    val failSet: List<VersionMigrateErrorDetail>
) {
    /**
     * 获取总的迁移包数量
     */
    fun getVersionCount(): Int = successSet.size + failSet.size
}