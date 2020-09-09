package com.tencent.bkrepo.repository.pojo.stage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ArtifactStageEnumTest {

    @Test
    @DisplayName("测试nextStage")
    fun testNextStage() {
        Assertions.assertEquals(ArtifactStageEnum.PRE_RELEASE, ArtifactStageEnum.NONE.nextStage())
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.PRE_RELEASE.nextStage())
        assertThrows<IllegalStateException> { ArtifactStageEnum.RELEASE.nextStage() }
    }

    @Test
    @DisplayName("测试upgrade")
    fun testUpgrade() {
        Assertions.assertEquals(ArtifactStageEnum.PRE_RELEASE, ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.PRE_RELEASE))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.RELEASE))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.PRE_RELEASE.upgrade(ArtifactStageEnum.RELEASE))

        assertThrows<IllegalStateException> { ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.NONE) }
        assertThrows<IllegalStateException> { ArtifactStageEnum.RELEASE.upgrade(ArtifactStageEnum.PRE_RELEASE) }
        assertThrows<IllegalStateException> { ArtifactStageEnum.RELEASE.upgrade(ArtifactStageEnum.RELEASE) }
    }

    @Test
    @DisplayName("测试ofTag")
    fun testOfTag() {
        Assertions.assertNull(ArtifactStageEnum.ofTag("xxxxxxx"))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTag(null))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault(null))
        Assertions.assertEquals(ArtifactStageEnum.PRE_RELEASE, ArtifactStageEnum.ofTagOrDefault("@prerelease"))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.ofTagOrDefault("@release"))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault("xxxx"))
    }

    @Test
    @DisplayName("测试getDisplayTag")
    fun testGetDisplayTag() {
        Assertions.assertEquals("", ArtifactStageEnum.NONE.getDisplayTag())
        Assertions.assertEquals("@prerelease", ArtifactStageEnum.PRE_RELEASE.getDisplayTag())
        Assertions.assertEquals("@prerelease,@release", ArtifactStageEnum.RELEASE.getDisplayTag())
    }
}