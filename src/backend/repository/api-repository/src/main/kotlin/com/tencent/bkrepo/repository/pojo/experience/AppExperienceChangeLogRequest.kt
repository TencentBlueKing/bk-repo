package com.tencent.bkrepo.repository.pojo.experience

import io.swagger.v3.oas.annotations.media.Schema

data class AppExperienceChangeLogRequest(
    @get:Schema(title = "文件名")
    val name: String? = null,

    @get:Schema(title = "版本号")
    val version: String? = null,

    @get:Schema(title = "版本描述")
    val remark: String? = null,

    @get:Schema(title = "发起人")
    val creator: String? = null,

    @get:Schema(title = "体验发起时间--起始时间(秒级)")
    val createDateBegin: Long? = null,

    @get:Schema(title = "体验发起时间--终止时间(秒级)")
    val createDateEnd: Long? = null,

    @get:Schema(title = "体验结束时间--起始时间(秒级)")
    val endDateBegin: Long? = null,

    @get:Schema(title = "体验结束时间--终止时间(秒级)")
    val endDateEnd: Long? = null,

    @get:Schema(title = "是否展示所有版本")
    val showAll: Boolean? = null,

    @get:Schema(title = "组织名称")
    val organizationName: String? = null,

    @get:Schema(title = "蓝盾灰度标识")
    val gray: String? = null,

    @get:Schema(title = "页目")
    val page: Long,

    @get:Schema(title = "每页数目")
    val pageSize: Long
)
