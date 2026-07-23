package com.tencent.bkrepo.repository.pojo.packages

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Package 元数据修复结果
 */
@Schema(title = "Package 元数据修复结果")
data class PackageMetadataRepairResult(
    @get:Schema(title = "本次修复涉及的 package 总数")
    val total: Int,
    @get:Schema(title = "元数据发生了实际更新的 package 数")
    val updated: Int,
    @get:Schema(title = "元数据无需修复的 package 数")
    val skipped: Int,
    @get:Schema(title = "修复失败的 package 数")
    val failed: Int,
    @get:Schema(title = "失败明细列表")
    val failedItems: List<FailedItem> = emptyList()
) {

    @Schema(title = "修复失败明细")
    data class FailedItem(
        @get:Schema(title = "包唯一标识")
        val packageKey: String,
        @get:Schema(title = "失败原因")
        val reason: String?
    )
}
