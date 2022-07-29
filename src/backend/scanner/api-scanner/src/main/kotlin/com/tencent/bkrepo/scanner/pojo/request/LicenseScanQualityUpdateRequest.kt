package com.tencent.bkrepo.scanner.pojo.request

data class LicenseScanQualityUpdateRequest(
    val recommend: Boolean = false,
    val compliance: Boolean = false,
    val unknown: Boolean = false,
    val forbidQualityUnPass: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map[LicenseScanQualityUpdateRequest::recommend.name] = recommend
        map[LicenseScanQualityUpdateRequest::compliance.name] = compliance
        map[LicenseScanQualityUpdateRequest::unknown.name] = unknown
        map[LicenseScanQualityUpdateRequest::forbidQualityUnPass.name] = forbidQualityUnPass
        return map
    }
}
