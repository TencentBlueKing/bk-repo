package com.tencent.bkrepo.scanner.pojo.response

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("制品许可扫描结果预览")
class FileLicensesResultOverview(
    @ApiModelProperty("子扫描任务id")
    val subTaskId: String,
    @ApiModelProperty("制品名")
    val name: String,
    @ApiModelProperty("packageKey")
    val packageKey: String? = null,
    @ApiModelProperty("制品版本")
    val version: String? = null,
    @ApiModelProperty("制品路径")
    val fullPath: String? = null,
    @ApiModelProperty("仓库类型")
    val repoType: String,
    @ApiModelProperty("仓库名")
    val repoName: String,

    @ApiModelProperty("高风险许可数")
    val high: Long = 0,
    @ApiModelProperty("中风险许可数")
    val medium: Long = 0,
    @ApiModelProperty("低风险许可数")
    val low: Long = 0,
    @ApiModelProperty("无风险许可数")
    val nil: Long = 0,
    @ApiModelProperty("许可总数")
    val total: Long = 0,

    @ApiModelProperty("完成时间")
    val finishTime: String?,
    @ApiModelProperty("是否通过质量规则")
    val qualityRedLine: Boolean? = null,
    @ApiModelProperty("扫描时方案的质量规则")
    val scanQuality: Map<String, Any>? = null,
    @ApiModelProperty("扫描时长")
    val duration: Long
)
