package com.tencent.bkrepo.scanner.pojo.request

data class ScanQualityCreateRequest(
    val critical: Int? = null,
    val high: Int? = null,
    val medium: Int? = null,
    val low: Int? = null,
    val forbidScanUnFinished: Boolean? = null,
    val forbidQualityUnPass: Boolean? = null
)
