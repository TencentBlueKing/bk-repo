package com.tencent.bkrepo.repository.pojo.clientupgrade

/**
 * 客户端 changelog 发布状态。
 * - DRAFT: 草稿；客户端只读接口不会返回
 * - PUBLISHED: 已发布；客户端可见
 */
enum class ClientChangelogStatus {
    DRAFT,
    PUBLISHED,
    ;
}
