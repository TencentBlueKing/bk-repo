package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.view.ViewModelProperties
import com.tencent.bkrepo.common.artifact.view.ViewModelService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.generic.UT_PROJECT_ID
import com.tencent.bkrepo.generic.UT_REPO_NAME
import com.tencent.bkrepo.generic.artifact.context.GenericArtifactSearchContext
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class DownloadServiceTest {

    private val nodeService: NodeService = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val downloadService = DownloadService(
        nodeService,
        ViewModelService(ViewModelProperties()),
        GenericProperties()
    ).apply {
        repository = artifactRepository
    }

    @BeforeEach
    fun setUp() {
        val repoDetail = mock<RepositoryDetail>()
        whenever(repoDetail.projectId).thenReturn(UT_PROJECT_ID)
        whenever(repoDetail.name).thenReturn(UT_REPO_NAME)
        whenever(repoDetail.storageCredentials).thenReturn(null)

        val request = mock<HttpServletRequest>()
        whenever(request.getAttribute(REPO_KEY)).thenReturn(repoDetail)
        whenever(request.getAttribute(ARTIFACT_INFO_KEY))
            .thenReturn(ArtifactInfo(UT_PROJECT_ID, UT_REPO_NAME, "/"))
        RequestContextHolder.setRequestAttributes(
            ServletRequestAttributes(request, mock<HttpServletResponse>())
        )
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `search should add fullPath to select when omitted`() {
        val queryModel = QueryModel(
            sort = null,
            select = listOf("name"),
            rule = Rule.QueryRule("name", "file", OperationType.EQ)
        )
        whenever(artifactRepository.search(any<ArtifactSearchContext>())).thenReturn(
            listOf(mapOf("category" to "LOCAL", "fullPath" to "/file1", "name" to "file1"))
        )

        val result = downloadService.search(queryModel)

        val captor = argumentCaptor<ArtifactSearchContext>()
        verify(artifactRepository).search(captor.capture())
        val usedSelect = (captor.firstValue as GenericArtifactSearchContext).queryModel!!.select!!
        assertTrue(usedSelect.contains(NodeInfo::fullPath.name))
        assertEquals(listOf("name"), queryModel.select)
        assertEquals(1, result.size)
        assertEquals("/file1", (result[0] as Map<*, *>)["fullPath"])
    }
}
