package com.tencent.bkrepo.preview.service.share

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.preview.config.ArtifactShareProperties
import com.tencent.bkrepo.preview.dao.ArtifactShareDao
import com.tencent.bkrepo.preview.model.TArtifactShare
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareUpsertRequest
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("作品分享 展示ID↔库内ID 转换")
class ArtifactShareServiceScopeMappingTest {

    private val artifactShareDao = mockk<ArtifactShareDao>()
    private val driveShareNodeResolver = mockk<DriveShareNodeResolver>()
    private val temporaryTokenClient = mockk<ServiceTemporaryTokenClient>()
    private val orgScopeIdMappingService = mockk<OrgScopeIdMappingService>()
    private val serviceUserClient = mockk<ServiceUserClient>(relaxed = true)
    private lateinit var service: ArtifactShareService

    @BeforeEach
    fun setUp() {
        service = ArtifactShareService(
            artifactShareDao = artifactShareDao,
            driveShareNodeResolver = driveShareNodeResolver,
            temporaryTokenClient = temporaryTokenClient,
            orgScopeIdMappingService = orgScopeIdMappingService,
            serviceUserClient = serviceUserClient,
            artifactShareProperties = ArtifactShareProperties(),
        )
    }

    @Test
    fun `upsert stores mapped ids and returns display ids`() {
        val node = mockk<TDriveNode>()
        every { node.createdBy } returns "alice"
        every {
            driveShareNodeResolver.resolveFileByIno("p", "r", 9L)
        } returns DriveShareNodeInfo(node = node, fullPath = "/a.html")
        every { driveShareNodeResolver.metadataValue(node, any()) } returns null
        every { orgScopeIdMappingService.toStoredIds(listOf("display-dept-1")) } returns mapOf(
            "display-dept-1" to "stored-dept-1",
        )
        every { orgScopeIdMappingService.toDisplayIds(listOf("stored-dept-1")) } returns mapOf(
            "stored-dept-1" to "display-dept-1",
        )
        every { artifactShareDao.findActiveByProjectRepoResourceId("p", "r", 9L) } returns null
        every {
            temporaryTokenClient.createToken(any())
        } returnsMany listOf(
            Response(0, null, listOf(tokenInfo("preview-token")), null),
            Response(0, null, listOf(tokenInfo("download-token")), null),
        )
        val saved = slot<TArtifactShare>()
        every { artifactShareDao.save(capture(saved)) } answers { saved.captured }

        val result = service.upsert(
            "alice",
            ArtifactShareUpsertRequest(
                projectId = "p",
                repoName = "r",
                resourceId = 9L,
                visibility = ShareVisibility.CUSTOM,
                userIds = listOf("bob"),
                orgIds = listOf("display-dept-1"),
            ),
        )

        assertEquals(listOf("bob"), saved.captured.userIds)
        assertEquals(listOf("stored-dept-1"), saved.captured.orgIds)
        assertEquals(listOf("bob"), result.userIds)
        assertEquals(listOf("display-dept-1"), result.orgIds)
        assertEquals("html", result.type)
    }

    @Test
    fun `upsert rejects unmapped org ids`() {
        val node = mockk<TDriveNode>()
        every { node.createdBy } returns "alice"
        every {
            driveShareNodeResolver.resolveFileByIno("p", "r", 9L)
        } returns DriveShareNodeInfo(node = node, fullPath = "/a.html")
        every { orgScopeIdMappingService.toStoredIds(listOf("display-missing")) } returns emptyMap()

        assertThrows(ErrorCodeException::class.java) {
            service.upsert(
                "alice",
                ArtifactShareUpsertRequest(
                    projectId = "p",
                    repoName = "r",
                    resourceId = 9L,
                    visibility = ShareVisibility.CUSTOM,
                    orgIds = listOf("display-missing"),
                ),
            )
        }
        verify(exactly = 0) { artifactShareDao.save(any()) }
    }

    private fun tokenInfo(token: String): TemporaryTokenInfo {
        return TemporaryTokenInfo(
            projectId = "p",
            repoName = "r",
            fullPath = "/a.html",
            token = token,
            authorizedUserList = emptySet(),
            authorizedOrgList = emptySet(),
            authorizedIpList = emptySet(),
            expireDate = null,
            permits = null,
            type = TokenType.PREVIEW,
            createdBy = "alice",
        )
    }
}
