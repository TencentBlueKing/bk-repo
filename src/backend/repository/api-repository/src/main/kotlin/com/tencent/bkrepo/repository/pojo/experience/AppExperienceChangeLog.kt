package com.tencent.bkrepo.repository.pojo.experience

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 版本体验-更新日志
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "版本体验-更新日志")
data class AppExperienceChangeLog(
    @get:Schema(description = "版本体验ID")
    val experienceHashId: String,

    @get:Schema(description = "体验名称")
    val experienceName: String,

    @get:Schema(description = "文件名称")
    val name: String,

    @get:Schema(description = "版本标题")
    val versionTitle: String,

    @get:Schema(description = "版本号")
    val version: String,

    @get:Schema(description = "更新日志")
    val changelog: String,

    @get:Schema(description = "版本体验BundleIdentifier")
    val bundleIdentifier: String,

    @get:Schema(description = "应用Scheme")
    val appScheme: String? = null,

    @get:Schema(description = "logo链接")
    val logoUrl: String,

    @get:Schema(description = "文件大小(byte)")
    val size: Long,

    @get:Schema(description = "是否过期")
    val expired: Boolean,

    @get:Schema(description = "上次下载的体验ID")
    val lastDownloadHashId: String,

    @get:Schema(description = "创建人")
    val creator: String,

    @get:Schema(description = "创建时间")
    val createDate: Long
)
