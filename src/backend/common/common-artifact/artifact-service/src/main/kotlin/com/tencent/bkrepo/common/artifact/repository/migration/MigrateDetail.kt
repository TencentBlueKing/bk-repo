package com.tencent.bkrepo.common.artifact.repository.migration

import java.time.Duration

/**
 * 迁移详情
 */
data class MigrateDetail(
    /**
     * 项目
     */
    val projectId: String,

    /**
     * 仓库
     */
    val repoName: String,

    /**
     * 迁移包列表
     */
    val packageList: MutableList<PackageMigrateDetail> = mutableListOf(),

    /**
     * 迁移总耗时
     */
    var duration: Duration = Duration.ZERO,

    /**
     * 描述
     */
    val description: String? = null
) {
    /**
     * 获取总的迁移包数量
     */
    fun getPackageCount(): Int = packageList.size

    fun addPackageMigrateDetail(packageMigrateDetail: PackageMigrateDetail) {
        packageList.add(packageMigrateDetail)
    }
}
