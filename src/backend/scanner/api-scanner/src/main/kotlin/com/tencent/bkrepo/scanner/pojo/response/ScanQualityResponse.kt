package com.tencent.bkrepo.scanner.pojo.response

import com.tencent.bkrepo.scanner.CVE_CRITICAL_COUNT
import com.tencent.bkrepo.scanner.CVE_HIGH_COUNT
import com.tencent.bkrepo.scanner.CVE_LOW
import com.tencent.bkrepo.scanner.CVE_MEDIUM_COUNT

data class ScanQualityResponse (
    val cveCritical: Int?,
    val cveHigh: Int?,
    val cveMid: Int?,
    val cveLow: Int?,
    val forbidScanUnFinished: Boolean,
    val forbidQualityUnPass: Boolean
){
    fun getScanQualityRedLineByLevel(level: String): Int {
        return when (level) {
            CVE_CRITICAL_COUNT -> cveCritical ?: 0
            CVE_HIGH_COUNT -> cveHigh ?: 0
            CVE_MEDIUM_COUNT -> cveMid ?: 0
            CVE_LOW -> cveLow ?: 0
            else -> 0
        }
    }
}