package com.tencent.bkrepo.executor.pojo.enums

enum class ScanTaskReport(val value: String) {
    CVESEC("cvesec"),
    CHECKSEC("checksec"),
    LICENSE("license"),
    SENSITIVE("sensitive")
}
