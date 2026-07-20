package com.tencent.bkrepo.common.metadata.pojo.shortlink

import java.time.LocalDateTime

/**
 * 创建短链接请求
 */
data class ShortLinkCreateRequest(
    /**
     * 目标 URL（相对路径，或以白名单 host 开头的绝对 URL）
     */
    val target: String,
    /**
     * 创建人
     */
    val createdBy: String,
    /**
     * 过期时间，为空则使用默认 TTL
     */
    val expiredDate: LocalDateTime? = null,
)
