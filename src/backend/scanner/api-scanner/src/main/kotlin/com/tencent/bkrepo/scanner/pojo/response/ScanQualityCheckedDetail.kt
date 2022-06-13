package com.tencent.bkrepo.scanner.pojo.response

data class ScanQualityCheckedDetail(
    val criticalStatus: ScanQualityCheckedStatus? = null,
    val highStatus: ScanQualityCheckedStatus? = null,
    val mediumStatus: ScanQualityCheckedStatus? = null,
    val lowStatus: ScanQualityCheckedStatus? = null,
    val qualityStatus: Boolean
) {
    data class ScanQualityCheckedStatus(
        val status: Boolean,
        val require: Long,
        val actual: Long
    )
}
