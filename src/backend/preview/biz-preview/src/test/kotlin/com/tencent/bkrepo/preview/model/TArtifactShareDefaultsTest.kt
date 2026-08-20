package com.tencent.bkrepo.preview.model

import com.tencent.bkrepo.preview.pojo.share.ArtifactShareResourceType
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("作品分享模型默认资源标记")
class TArtifactShareDefaultsTest {

    @Test
    fun `defaults to drive node`() {
        val now = LocalDateTime.now()
        val record = TArtifactShare(
            createdBy = "owner",
            createdDate = now,
            lastModifiedBy = "owner",
            lastModifiedDate = now,
            projectId = "p",
            repoName = "r",
            resourceId = 1L,
            fullPath = "/a.txt",
            visibility = ShareVisibility.PUBLIC,
        )
        assertEquals(ArtifactShareResourceType.DRIVE_NODE, record.resourceType)
        assertEquals(1L, record.resourceId)
        assertEquals(false, record.featured)
        assertEquals(null, record.artifactType)
    }
}
