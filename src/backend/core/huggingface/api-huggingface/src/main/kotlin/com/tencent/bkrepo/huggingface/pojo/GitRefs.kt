package com.tencent.bkrepo.huggingface.pojo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Git引用信息")
data class GitRefs(
    @get:Schema(title = "分支列表")
    val branches: List<GitRefInfo>,
    @get:Schema(title = "转换列表")
    val converts: List<GitRefInfo>,
    @get:Schema(title = "标签列表")
    val tags: List<GitRefInfo>,
    @get:Schema(title = "拉取请求列表")
    val pullRequests: List<GitRefInfo>? = null
)

@Schema(title = "Git引用详情")
data class GitRefInfo(
    @get:Schema(title = "名称")
    val name: String,
    @get:Schema(title = "引用")
    val ref: String,
    @get:Schema(title = "目标提交")
    val targetCommit: String,
)
