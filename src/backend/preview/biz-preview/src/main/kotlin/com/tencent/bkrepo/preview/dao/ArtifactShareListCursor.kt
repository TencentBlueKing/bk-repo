package com.tencent.bkrepo.preview.dao

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * 作品分享列表游标：按 lastModifiedDate DESC, id DESC 的 keyset。
 */
data class ArtifactShareListCursor(
    val lastModifiedDate: LocalDateTime,
    val id: String,
) {

    fun encode(): String {
        val payload = "${lastModifiedDate.format(FORMATTER)}$SEPARATOR$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private const val SEPARATOR = "|"

        fun decode(raw: String?): ArtifactShareListCursor? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) {
                return null
            }
            val payload = runCatching {
                String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            }.getOrElse {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "cursor")
            }
            val separatorIndex = payload.indexOf(SEPARATOR)
            if (separatorIndex <= 0 || separatorIndex == payload.lastIndex) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "cursor")
            }
            val timePart = payload.substring(0, separatorIndex)
            val idPart = payload.substring(separatorIndex + 1).trim()
            if (idPart.isEmpty()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "cursor")
            }
            val lastModifiedDate = runCatching { LocalDateTime.parse(timePart, FORMATTER) }.getOrElse {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "cursor")
            }
            return ArtifactShareListCursor(lastModifiedDate = lastModifiedDate, id = idPart)
        }
    }
}
