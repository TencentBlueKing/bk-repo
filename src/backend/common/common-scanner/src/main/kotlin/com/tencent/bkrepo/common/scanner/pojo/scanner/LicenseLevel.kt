package com.tencent.bkrepo.common.scanner.pojo.scanner

enum class LicenseLevel(val levelName: String, val level: Int) {
    HIGH("critical", 3),
    MEDIUM("high", 2),
    LOW("medium", 1),
    NIL("nil", 0);
}
