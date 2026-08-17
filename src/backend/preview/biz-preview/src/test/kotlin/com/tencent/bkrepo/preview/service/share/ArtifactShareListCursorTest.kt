package com.tencent.bkrepo.preview.service.share

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.preview.dao.ArtifactShareListCursor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("作品分享列表游标")
class ArtifactShareListCursorTest {

    @Test
    fun `blank cursor decodes to null`() {
        assertNull(ArtifactShareListCursor.decode(null))
        assertNull(ArtifactShareListCursor.decode("  "))
    }

    @Test
    fun `encode then decode round-trips`() {
        val cursor = ArtifactShareListCursor(
            lastModifiedDate = LocalDateTime.of(2026, 8, 13, 15, 4, 5, 123000000),
            id = "abc123",
        )
        assertEquals(cursor, ArtifactShareListCursor.decode(cursor.encode()))
    }

    @Test
    fun `invalid cursor throws parameter invalid`() {
        val exception = assertThrows(ErrorCodeException::class.java) {
            ArtifactShareListCursor.decode("not-a-cursor")
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, exception.messageCode)
    }
}
