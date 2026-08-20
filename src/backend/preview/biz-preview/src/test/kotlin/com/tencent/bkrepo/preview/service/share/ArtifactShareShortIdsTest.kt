package com.tencent.bkrepo.preview.service.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("作品分享短链码格式")
class ArtifactShareShortIdsTest {

    @Test
    fun `accepts 8-char base62`() {
        assertTrue(ArtifactShareShortIds.isValid("Ab12Cd34"))
        assertTrue(ArtifactShareShortIds.isValid("00000000"))
        assertTrue(ArtifactShareShortIds.isValid("zzzzzzzz"))
    }

    @Test
    fun `rejects wrong length or charset`() {
        assertFalse(ArtifactShareShortIds.isValid(""))
        assertFalse(ArtifactShareShortIds.isValid("Ab12Cd3"))
        assertFalse(ArtifactShareShortIds.isValid("Ab12Cd345"))
        assertFalse(ArtifactShareShortIds.isValid("Ab12Cd3-"))
        assertFalse(ArtifactShareShortIds.isValid("ab12cd3/"))
        assertFalse(ArtifactShareShortIds.isValid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee".replace("-", "")))
    }

    @Test
    fun `generate returns 8-char base62`() {
        val value = ArtifactShareShortIds.generate()
        assertEquals(ArtifactShareShortIds.LENGTH, value.length)
        assertTrue(ArtifactShareShortIds.isValid(value))
    }
}
