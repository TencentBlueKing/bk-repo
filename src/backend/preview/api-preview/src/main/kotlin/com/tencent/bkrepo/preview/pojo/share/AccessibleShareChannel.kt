package com.tencent.bkrepo.preview.pojo.share

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "可访问作品列表的权限筛选")
enum class AccessibleShareChannel {
    /**
     * 公开 + 指定给当前用户或所属部门
     */
    ALL,

    /**
     * 社区公开：visibility=PUBLIC
     */
    PUBLIC,

    /**
     * 指定分享：visibility=CUSTOM，且当前用户被点名或部门命中
     */
    CUSTOM,
}
