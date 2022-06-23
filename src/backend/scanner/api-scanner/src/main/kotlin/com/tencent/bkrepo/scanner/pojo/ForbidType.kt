package com.tencent.bkrepo.scanner.pojo

/**
 * 扫描禁用类型
 */
enum class ForbidType {
    // 扫描中被禁用
    SCANNING,
    // 未通过质量规则被禁用
    QUALITYUNPASS,
    // 手动禁用
    MANUAL
}
