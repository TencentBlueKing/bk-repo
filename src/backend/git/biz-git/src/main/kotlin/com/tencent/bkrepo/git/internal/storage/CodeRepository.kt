package com.tencent.bkrepo.git.internal.storage

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.git.constant.DEFAULT_BRANCH
import com.tencent.bkrepo.git.constant.HEAD
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase
import org.eclipse.jgit.internal.storage.dfs.DfsRepository
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.RefDatabase
import org.slf4j.LoggerFactory

/**
 * Git DFS repository
 *
 * 基于bkrepo的存储能力实现的git DFS仓库
 * */
class CodeRepository(
    val projectId: String,
    val repoName: String,
    val storageCredentials: StorageCredentials?,
    val dataService: RepositoryDataService,
    val blockSize: Int,
    builder: DfsRepositoryBuilder<CodeRepositoryBuilder, CodeRepository>
) : DfsRepository(builder) {

    private val objDatabase: CodeObjDatabase = CodeObjDatabase(this, builder.readerOptions, dataService, blockSize)
    private val refDatabase: CodeRefDatabase = CodeRefDatabase(this)

    override fun getObjectDatabase(): DfsObjDatabase {
        return objDatabase
    }

    override fun getRefDatabase(): RefDatabase {
        return refDatabase
    }

    override fun autoGC(monitor: ProgressMonitor?) {
        super.autoGC(monitor)
    }

    fun setDefaultBranch(ref: String) {
        val refUpdate = this.updateRef(HEAD)
        refUpdate.link(ref)
        refUpdate.update()
        logger.info("${this.identifier} set default branch $DEFAULT_BRANCH")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeRepository::class.java)
    }
}
