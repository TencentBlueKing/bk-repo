package com.tencent.bkrepo.common.metadata.service.shortlink

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkListOption

/**
 * 短链接响应式服务
 */
interface RShortLinkService {

    /**
     * 创建短链接
     */
    suspend fun create(request: ShortLinkCreateRequest): ShortLink

    /**
     * 解析短码为目标绝对 URL；不存在抛 404，过期抛 410
     */
    suspend fun resolve(code: String, scheme: String, host: String): String

    /**
     * 按短码查询（含已过期记录）
     */
    suspend fun get(code: String): ShortLink?

    /**
     * 硬删短链接；不存在抛 404
     */
    suspend fun delete(code: String)

    /**
     * 按创建人分页查询
     */
    suspend fun listByCreator(option: ShortLinkListOption): Page<ShortLink>
}
