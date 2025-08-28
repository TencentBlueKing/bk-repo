package com.tencent.bkrepo.repository.pojo.experience

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "版本体验-版本信息")
data class AppExperience(
    @get:Schema(title = "应用Scheme")
    val appScheme: String? = null,

    @get:Schema(title = "版本体验BundleIdentifier")
    val bundleIdentifier: String,

    @get:Schema(title = "产品类别")
    val categoryId: Long,

    @get:Schema(title = "分类标签")
    val classify: String? = null,

    @get:Schema(title = "创建时间")
    val createDate: Long,

    @get:Schema(title = "版本体验ID")
    val experienceHashId: String,

    @get:Schema(title = "体验名称")
    val experienceName: String,

    @get:Schema(title = "是否过期")
    val expired: Boolean,

    @get:Schema(title = "上次下载的体验ID")
    val lastDownloadHashId: String,

    @get:Schema(title = "logo链接")
    val logoUrl: String,

    @get:Schema(title = "版本名称")
    val name: String,

    @get:Schema(title = "平台")
    val platform: AppExperiencePlatform,

    @get:Schema(title = "产品负责人")
    val productOwner: List<String>,

    @get:Schema(title = "是否展示红点")
    val redPointEnabled: Boolean? = null,

    @get:Schema(title = "文件大小(byte)")
    val size: Long,

    @get:Schema(title = "来源")
    val source: AppExperienceSource,

    @get:Schema(title = "是否订阅")
    val subscribe: Boolean,

    @get:Schema(title = "版本体验版本号")
    val version: String,

    @get:Schema(title = "版本标题")
    val versionTitle: String
)
