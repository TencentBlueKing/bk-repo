package com.tencent.bkrepo.repository.pojo.experience

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 体验--安装包
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "体验--安装包")
data class AppExperienceInstallPackage(
    @get:Schema(description = "仓库类型")
    val artifactoryType: String,

    @get:Schema(description = "是否有跳转构件详情的权限")
    val detailPermission: Boolean,

    @get:Schema(description = "名称")
    val name: String,

    @get:Schema(description = "路径")
    val path: String,

    @get:Schema(description = "项目ID")
    val projectId: String,

    @get:Schema(description = "大小")
    val size: Long
)