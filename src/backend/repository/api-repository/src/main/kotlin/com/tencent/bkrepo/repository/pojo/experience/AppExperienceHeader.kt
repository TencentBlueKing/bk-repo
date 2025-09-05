package com.tencent.bkrepo.repository.pojo.experience

import io.swagger.v3.oas.annotations.media.Schema

/**
 * App体验通用请求
 */
data class AppExperienceHeader(
    @get:Schema(title = "APP平台")
    val platform: String?,
    @get:Schema(title = "APP版本")
    var version: String?,
    @get:Schema(title = "APP组织名称")
    val organization: String?,
)
