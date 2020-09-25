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
        assertThrows<IllegalArgumentException> { ArtifactStageEnum.RELEASE.nextStage() }
    }

    @Test
    @DisplayName("测试upgrade")
    fun testUpgrade() {
        Assertions.assertEquals(ArtifactStageEnum.PRE_RELEASE, ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.PRE_RELEASE))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.RELEASE))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.PRE_RELEASE.upgrade(ArtifactStageEnum.RELEASE))

        assertThrows<IllegalArgumentException> { ArtifactStageEnum.NONE.upgrade(ArtifactStageEnum.NONE) }
        assertThrows<IllegalArgumentException> { ArtifactStageEnum.RELEASE.upgrade(ArtifactStageEnum.PRE_RELEASE) }
        assertThrows<IllegalArgumentException> { ArtifactStageEnum.RELEASE.upgrade(ArtifactStageEnum.RELEASE) }
    }

    @Test
    @DisplayName("测试ofTagOrDefault")
    fun testOfTagOrDefault() {
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault(""))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault("  "))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault(null))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.ofTagOrDefault("@release"))
        Assertions.assertEquals(ArtifactStageEnum.NONE, ArtifactStageEnum.ofTagOrDefault("xxx"))
    }

    @Test
    @DisplayName("测试ofTagOrNull")
    fun testOfTagOrNull() {
        Assertions.assertNull(ArtifactStageEnum.ofTagOrNull(""))
        Assertions.assertNull(ArtifactStageEnum.ofTagOrNull("  "))
        Assertions.assertNull(ArtifactStageEnum.ofTagOrNull(null))
        Assertions.assertEquals(ArtifactStageEnum.RELEASE, ArtifactStageEnum.ofTagOrNull("@release"))
        assertThrows<IllegalArgumentException> { ArtifactStageEnum.ofTagOrNull("xxx") }
    }
}