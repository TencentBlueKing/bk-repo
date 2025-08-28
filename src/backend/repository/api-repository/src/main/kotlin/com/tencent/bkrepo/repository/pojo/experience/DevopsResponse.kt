package com.tencent.bkrepo.repository.pojo.experience

/**
 * CI 体验平台通用返回格式（仅内部反序列化用）
 */
data class DevopsResponse<T>(
    val status: Int,
    val message: String? = null,
    val data: T? = null
)
