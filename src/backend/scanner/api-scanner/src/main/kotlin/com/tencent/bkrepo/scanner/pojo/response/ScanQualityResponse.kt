package com.tencent.bkrepo.scanner.pojo.response

import com.tencent.bkrepo.common.scanner.pojo.scanner.Level

data class ScanQualityResponse(
    val critical: Long?,
    val high: Long?,
    val medium: Long?,
    val low: Long?,
    val forbidScanUnFinished: Boolean,
    val forbidQualityUnPass: Boolean
) {

    companion object {
        fun create(map: Map<String, Any>) = ScanQualityResponse(
            critical = map[Level.CRITICAL.levelName] as? Long,
            high = map[Level.HIGH.levelName] as? Long,
            medium = map[Level.MEDIUM.levelName] as? Long,
            low = map[Level.LOW.levelName] as? Long,
            forbidScanUnFinished = map[ScanQualityResponse::forbidScanUnFinished.name] as? Boolean ?: false,
            forbidQualityUnPass = map[ScanQualityResponse::forbidQualityUnPass.name] as? Boolean ?: false
        )
    }
}
