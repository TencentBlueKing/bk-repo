package com.tencent.bkrepo.common.metadata.pojo.shortlink

import java.time.LocalDateTime

/**
 * 短链接信息
 */
data class ShortLink(
    /**
     * 短码
     */
    val code: String,
    /**
     * 目标 URL（相对路径或绝对内部 URL）
     */
    val target: String,
    /**
     * 完整短链地址，如 https://host/t/{code}
     */
    val shortUrl: String,
    /**
     * 过期时间
     */
    val expiredDate: LocalDateTime,
    /**
     * 创建人
     */
    val createdBy: String,
    /**
     * 创建时间
     */
    val createdDate: LocalDateTime,
)
