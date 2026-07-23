package com.tencent.bkrepo.common.metadata.service.packages

import com.tencent.bkrepo.repository.pojo.packages.PackageMetadataRepairResult

interface PackageRepairService {

    /**
     * 修复npm历史版本数据
     */
    fun repairHistoryVersion()

    /**
     * 修正包的版本数
     */
    fun repairVersionCount()

    /**
     * 按范围修复 Package 元数据字段（latest、historyVersion）。
     *
     * 以 package_version 集合为权威数据源：
     * 1. latest 重算为 ordinal DESC 排序的第一个版本；
     * 2. historyVersion 全量覆盖为当前所有版本名的集合。
     *
     * @param projectId 项目 ID，必填
     * @param repoName 仓库名，必填
     * @param packageKey 包唯一标识；为空时修复该仓库下所有 package
     * @return 修复结果统计
     */
    fun repairPackageMetadata(
        projectId: String,
        repoName: String,
        packageKey: String? = null
    ): PackageMetadataRepairResult
}
