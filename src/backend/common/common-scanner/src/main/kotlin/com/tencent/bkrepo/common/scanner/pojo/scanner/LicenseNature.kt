package com.tencent.bkrepo.common.scanner.pojo.scanner

enum class LicenseNature(val natureName: String, val level: String) {
    UN_COMPLIANCE("unCompliance", "compliance"),
    UN_RECOMMEND("unRecommend", "recommend"),
    UNKNOWN("unknown", "unknown"),
    NORMAL("normal", "normal");
}
