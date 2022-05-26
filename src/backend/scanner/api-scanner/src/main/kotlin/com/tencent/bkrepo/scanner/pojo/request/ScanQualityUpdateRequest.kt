package com.tencent.bkrepo.scanner.pojo.request

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.scanner.pojo.scanner.Level

data class ScanQualityUpdateRequest(
    val critical: Long? = null,
    val high: Long? = null,
    val medium: Long? = null,
    val low: Long? = null,
    val forbidScanUnFinished: Boolean? = null,
    val forbidQualityUnPass: Boolean? = null
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        Level.values().forEach { level ->
            val methodName = "get${level.levelName.capitalize()}"
            val method = ScanQualityUpdateRequest::class.java.getDeclaredMethod(methodName)

            val redLine = method.invoke(this) as Long?
            if (redLine != null && redLine < 0) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, level.levelName)
            }
            redLine?.let { map[level.levelName] = it }
        }
        this.forbidScanUnFinished?.let { map[ScanQualityUpdateRequest::forbidScanUnFinished.name] = it }
        this.forbidQualityUnPass?.let { map[ScanQualityUpdateRequest::forbidQualityUnPass.name] = it }
        return map
    }
}
