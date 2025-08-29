package com.tencent.bkrepo.repository.pojo.experience

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 版本体验-版本详情
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "版本体验-版本详情")
data class AppExperienceDetail(
    @get:Schema(description = "应用Scheme")
    val appScheme: String? = null,

    @get:Schema(description = "版本体验BundleIdentifier")
    val bundleIdentifier: String,

    @get:Schema(description = "是否可体验")
    val canExperience: Boolean,

    @get:Schema(description = "产品类别")
    val categoryId: Long,

    @get:Schema(description = "更新日志")
    val changeLog: List<AppExperienceChangeLog>,

    @get:Schema(description = "创建时间")
    val createDate: Long,

    @get:Schema(description = "体验截至时间")
    val endDate: Long,

    @get:Schema(description = "体验状态")
    val experienceCondition: Long,

    @get:Schema(description = "版本体验ID")
    val experienceHashId: String,

    @get:Schema(description = "体验名称")
    val experienceName: String,

    @get:Schema(description = "是否已过期")
    val expired: Boolean,

    @get:Schema(description = "上次下载的体验ID")
    val lastDownloadHashId: String,

    @get:Schema(description = "logo链接")
    val logoUrl: String,

    @get:Schema(description = "版本名称")
    val name: String,

    @get:Schema(description = "是否在线")
    val online: Boolean,

    @get:Schema(description = "包名称")
    val packageName: String,

    @get:Schema(description = "平台")
    val platform: AppExperiencePlatform,

    @get:Schema(description = "产品负责人")
    val productOwner: List<String>,

    @get:Schema(description = "是否为公开体验")
    val publicExperience: Boolean,

    @get:Schema(description = "描述")
    val remark: String,

    @get:Schema(description = "分享链接")
    val shareUrl: String,

    @get:Schema(description = "文件大小(byte)")
    val size: Long,

    @get:Schema(description = "是否订阅")
    val subscribe: Boolean,

    @get:Schema(description = "版本体验版本号")
    val version: String,

    @get:Schema(description = "版本标题")
    val versionTitle: String
)