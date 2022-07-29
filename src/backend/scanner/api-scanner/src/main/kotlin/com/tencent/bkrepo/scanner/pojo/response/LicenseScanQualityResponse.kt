package com.tencent.bkrepo.scanner.pojo.response

class LicenseScanQualityResponse(
    val recommend: Boolean?,
    val compliance: Boolean?,
    val unknown: Boolean?,
    val forbidQualityUnPass: Boolean?
) {
    companion object {
        fun create(map: Map<String, Any>) = LicenseScanQualityResponse(
            recommend = map[LicenseScanQualityResponse::recommend.name] as? Boolean,
            compliance = map[LicenseScanQualityResponse::compliance.name] as? Boolean,
            unknown = map[LicenseScanQualityResponse::unknown.name] as? Boolean,
            forbidQualityUnPass = map[LicenseScanQualityResponse::forbidQualityUnPass.name] as? Boolean
        )
    }
}
