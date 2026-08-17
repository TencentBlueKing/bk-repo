package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "作品分享可见性")
enum class ShareVisibility {
    /** 任意已登录内网用户 */
    PUBLIC,

    /** 指定组织/人员范围（可同时存在，OR 命中） */
    CUSTOM,
}
