package com.tencent.bkrepo.job.separation.executor

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationTaskDao
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.NodeFilterInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.PackageFilterInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import com.tencent.bkrepo.common.metadata.pojo.separation.VersionFilterInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.record.SeparationContext
import com.tencent.bkrepo.common.metadata.service.separation.DataRestorer
import com.tencent.bkrepo.common.metadata.service.separation.DataSeparator
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE_ARCHIVED
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.SEPARATE
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.SEPARATE_ARCHIVED
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("ColdDataSeparate / ColdDataRestore 执行器")
class ColdDataSeparationExecutorsTest {

    private val separationTaskDao = mockk<SeparationTaskDao>(relaxed = true)
    private val dataSeparationConfig = DataSeparationConfig()
    private lateinit var recorderSep: RecordingDataSeparator
    private lateinit var recorderRestore: RecordingDataRestorer
    private lateinit var separateExecutor: ColdDataSeparateTaskExecutor
    private lateinit var restoreExecutor: ColdDataRestoreTaskExecutor

    @BeforeEach
    fun setup() {
        recorderSep = RecordingDataSeparator()
        recorderRestore = RecordingDataRestorer()
        separateExecutor = ColdDataSeparateTaskExecutor(separationTaskDao, dataSeparationConfig, recorderSep)
        restoreExecutor = ColdDataRestoreTaskExecutor(separationTaskDao, dataSeparationConfig, recorderRestore)
    }

    @Test
    @DisplayName("SEPARATE 且内容为空时走整仓 repoSeparator")
    fun separate_wholeRepo_callsRepoSeparator() {
        val ctx = ctx(SEPARATE, SeparationContent())
        separateExecutor.doAction(ctx)
        assertEquals(1, recorderSep.repoSeparatorCalls.size)
        assertSame(ctx, recorderSep.repoSeparatorCalls[0])
        assertTrue(recorderSep.archivedWholeRepoCalls.isEmpty())
        assertTrue(recorderSep.archivedPathCalls.isEmpty())
    }

    @Test
    @DisplayName("SEPARATE_ARCHIVED 且内容为空时整仓 archived 节点降冷")
    fun separateArchived_wholeRepo_callsArchivedNodeSeparator() {
        val ctx = ctx(SEPARATE_ARCHIVED, SeparationContent())
        separateExecutor.doAction(ctx)
        assertEquals(1, recorderSep.archivedWholeRepoCalls.size)
        assertSame(ctx, recorderSep.archivedWholeRepoCalls[0])
        assertTrue(recorderSep.repoSeparatorCalls.isEmpty())
    }

    @Test
    @DisplayName("SEPARATE_ARCHIVED 指定 paths 时按路径降冷 archived 节点")
    fun separateArchived_withPaths_callsArchivedNodeSeparatorPerPath() {
        val path = NodeFilterInfo(path = "/a/")
        val ctx = ctx(SEPARATE_ARCHIVED, SeparationContent(paths = mutableListOf(path)))
        separateExecutor.doAction(ctx)
        assertEquals(listOf(ctx to path), recorderSep.archivedPathCalls)
    }

    @Test
    @DisplayName("SEPARATE 指定 paths 时按路径 nodeSeparator")
    fun separate_withPaths_callsNodeSeparatorPerPath() {
        val path = NodeFilterInfo(path = "/p/")
        val ctx = ctx(SEPARATE, SeparationContent(paths = mutableListOf(path)))
        separateExecutor.doAction(ctx)
        assertEquals(listOf(ctx to path), recorderSep.nodeCalls)
    }

    @Test
    @DisplayName("RESTORE 且内容为空时整仓 repoRestorer")
    fun restore_wholeRepo_callsRepoRestorer() {
        val ctx = ctx(RESTORE, SeparationContent())
        restoreExecutor.doAction(ctx)
        assertEquals(1, recorderRestore.repoRestorerCalls.size)
        assertSame(ctx, recorderRestore.repoRestorerCalls[0])
    }

    @Test
    @DisplayName("RESTORE 指定 paths 时按路径 nodeRestorer")
    fun restore_withPaths_callsNodeRestorerPerPath() {
        val path = NodeFilterInfo(path = "/p/")
        val ctx = ctx(RESTORE, SeparationContent(paths = mutableListOf(path)))
        restoreExecutor.doAction(ctx)
        assertEquals(listOf(ctx to path), recorderRestore.nodeCalls)
    }

    @Test
    @DisplayName("RESTORE_ARCHIVED 且内容为空时整仓 archivedNodeRestorer")
    fun restoreArchived_wholeRepo_callsArchivedNodeRestorer() {
        val ctx = ctx(RESTORE_ARCHIVED, SeparationContent())
        restoreExecutor.doAction(ctx)
        assertEquals(1, recorderRestore.archivedWholeRepoCalls.size)
        assertSame(ctx, recorderRestore.archivedWholeRepoCalls[0])
        assertTrue(recorderRestore.repoRestorerCalls.isEmpty())
    }

    @Test
    @DisplayName("RESTORE_ARCHIVED 指定 paths 时按路径恢复 archived 冷节点")
    fun restoreArchived_withPaths_callsArchivedNodeRestorerPerPath() {
        val path = NodeFilterInfo(path = "/b/")
        val ctx = ctx(RESTORE_ARCHIVED, SeparationContent(paths = mutableListOf(path)))
        restoreExecutor.doAction(ctx)
        assertEquals(listOf(ctx to path), recorderRestore.archivedPathCalls)
    }

    @Test
    @DisplayName("SEPARATE_ARCHIVED 仅填 packages 时当前实现不调用 separator（边界行为）")
    fun separateArchived_packagesOnly_noSeparatorCall() {
        val pkg = PackageFilterInfo(packageKey = "k")
        val ctx = ctx(SEPARATE_ARCHIVED, SeparationContent(packages = mutableListOf(pkg)))
        separateExecutor.doAction(ctx)
        assertTrue(recorderSep.repoSeparatorCalls.isEmpty())
        assertTrue(recorderSep.archivedWholeRepoCalls.isEmpty())
        assertTrue(recorderSep.archivedPathCalls.isEmpty())
        assertTrue(recorderSep.nodeCalls.isEmpty())
        assertTrue(recorderSep.packageCalls.isEmpty())
    }

    private fun ctx(type: String, content: SeparationContent): SeparationContext {
        val task = TSeparationTask(
            id = "tid",
            projectId = "p",
            repoName = "r",
            createdBy = "u",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "u",
            lastModifiedDate = LocalDateTime.now(),
            separationDate = LocalDateTime.now(),
            content = content,
            type = type,
        )
        val repo = RepositoryDetail(
            projectId = "p",
            name = "r",
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.LOCAL,
            public = false,
            description = null,
            configuration = LocalConfiguration(),
            storageCredentials = null,
            oldCredentialsKey = null,
            createdBy = "",
            createdDate = "",
            lastModifiedBy = "",
            lastModifiedDate = "",
            quota = null,
            used = null,
        )
        return SeparationContext(task, repo)
    }
}

private class RecordingDataSeparator : DataSeparator {
    val repoSeparatorCalls = mutableListOf<SeparationContext>()
    val packageCalls = mutableListOf<Pair<SeparationContext, PackageFilterInfo>>()
    val nodeCalls = mutableListOf<Pair<SeparationContext, NodeFilterInfo>>()
    val archivedWholeRepoCalls = mutableListOf<SeparationContext>()
    val archivedPathCalls = mutableListOf<Pair<SeparationContext, NodeFilterInfo>>()

    override fun repoSeparator(context: SeparationContext) {
        repoSeparatorCalls.add(context)
    }

    override fun packageSeparator(context: SeparationContext, pkg: PackageFilterInfo) {
        packageCalls.add(context to pkg)
    }

    override fun versionSeparator(context: SeparationContext, version: VersionFilterInfo) {}

    override fun nodeSeparator(context: SeparationContext, node: NodeFilterInfo) {
        nodeCalls.add(context to node)
    }

    override fun archivedNodeSeparator(context: SeparationContext, node: NodeFilterInfo?) {
        if (node == null) archivedWholeRepoCalls.add(context) else archivedPathCalls.add(context to node)
    }
}

private class RecordingDataRestorer : DataRestorer {
    val repoRestorerCalls = mutableListOf<SeparationContext>()
    val packageCalls = mutableListOf<Pair<SeparationContext, PackageFilterInfo>>()
    val nodeCalls = mutableListOf<Pair<SeparationContext, NodeFilterInfo>>()
    val archivedWholeRepoCalls = mutableListOf<SeparationContext>()
    val archivedPathCalls = mutableListOf<Pair<SeparationContext, NodeFilterInfo>>()

    override fun repoRestorer(context: SeparationContext) {
        repoRestorerCalls.add(context)
    }

    override fun packageRestorer(context: SeparationContext, pkg: PackageFilterInfo) {
        packageCalls.add(context to pkg)
    }

    override fun versionRestorer(context: SeparationContext, version: VersionFilterInfo) {}

    override fun nodeRestorer(context: SeparationContext, node: NodeFilterInfo) {
        nodeCalls.add(context to node)
    }

    override fun archivedNodeRestorer(context: SeparationContext, node: NodeFilterInfo?) {
        if (node == null) archivedWholeRepoCalls.add(context) else archivedPathCalls.add(context to node)
    }
}
