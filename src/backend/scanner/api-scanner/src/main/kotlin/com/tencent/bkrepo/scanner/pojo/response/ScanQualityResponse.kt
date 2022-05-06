package com.tencent.bkrepo.scanner.pojo.response

import com.tencent.bkrepo.scanner.CVE_CRITICAL_COUNT
import com.tencent.bkrepo.scanner.CVE_HIGH_COUNT
import com.tencent.bkrepo.scanner.CVE_LOW_COUNT
import com.tencent.bkrepo.scanner.CVE_MEDIUM_COUNT

data class ScanQualityResponse (
    val critical: Int?,
    val high: Int?,
    val medium: Int?,
    val low: Int?,
    val forbidScanUnFinished: Boolean,
    val forbidQualityUnPass: Boolean
){
    fun getScanQualityRedLineByLevel(level: String): Int {
        return when (level) {
            CVE_CRITICAL_COUNT -> critical ?: 0
            CVE_HIGH_COUNT -> high ?: 0
            CVE_MEDIUM_COUNT -> medium ?: 0
            CVE_LOW_COUNT -> low ?: 0
            else -> 0
        }
    }
}