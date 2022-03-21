package com.tencent.bkrepo.scanner.pojo

/**
 * 漏洞类型
 */
enum class LeakType(val value: String) {
    CRITICAL("危急"),
    HIGH("高危"),
    LOW("低危"),
    MEDIUM("中危");
}
