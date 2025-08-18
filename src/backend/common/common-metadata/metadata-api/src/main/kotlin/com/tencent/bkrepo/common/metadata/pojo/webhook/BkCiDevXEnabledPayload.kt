package com.tencent.bkrepo.common.metadata.pojo.webhook

/**
 * 蓝盾开启DevX时发送的Webhook Payload
 */
data class BkCiDevXEnabledPayload(
    /**
     * 项目名称
     */
    val projectName: String,
    /**
     * 项目代码（蓝盾项目Id）
     */
    val projectCode: String,
    /**
     * 事业群ID
     */
    val bgId: String?,
    /**
     * 事业群名字
     */
    val bgName: String?,
    /**
     * 中心ID
     */
    val centerId: String?,
    /**
     * 中心名称
     */
    val centerName: String?,
    /**
     * 部门ID
     */
    val deptId: String?,
    /**
     * 部门名称
     */
    val deptName: String?,
    /**
     * 英文缩写
     */
    val englishName: String,
    /**
     * 运营产品ID
     */
    val productId: Int?,
)