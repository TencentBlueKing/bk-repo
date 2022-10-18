package com.tencent.bkrepo.git.internal.storage

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.git.constant.DEFAULT_BRANCH
import com.tencent.bkrepo.git.constant.HEAD
import com.tencent.bkrepo.git.exception.LockFailedException
import java.time.Duration
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
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
    builder: DfsRepositoryBuilder<CodeRepositoryBuilder, CodeRepository>,
    private val lockProvider: LockProvider
) : DfsRepository(builder) {

    private val objDatabase: CodeObjDatabase = CodeObjDatabase(this, builder.readerOptions, dataService, blockSize)
    private val refDatabase: CodeRefDatabase = CodeRefDatabase(this)

    private var lock: SimpleLock? = null

    override fun getObjectDatabase(): DfsObjDatabase {
        return objDatabase
    }

    override fun getRefDatabase(): RefDatabase {
        return refDatabase
    }

    override fun autoGC(monitor: ProgressMonitor?) {
        super.autoGC(monitor)
    }

    fun lock() {
        val lockOptional = lockProvider.lock(getLockConfiguration())
        if (!lockOptional.isPresent) {
            throw LockFailedException(getLockKey())
        }
        lock = lockOptional.get()
    }

    fun unLock() {
        lock?.let {
            it.unlock()
            lock = null
        }
    }

    private fun getLockConfiguration(): LockConfiguration {
        return LockConfiguration(
            getLockKey(),
            Duration.ofMillis(LOCK_AT_MOST_FOR),
            Duration.ofMillis(LOCK_AT_LEAST_FOR)
        )
    }

    private fun getLockKey(): String {
        return LOCK_PREFIX + identifier
    }

    fun setDefaultBranch(ref: String) {
        val refUpdate = this.updateRef(HEAD)
        refUpdate.link(ref)
        refUpdate.update()
        logger.info("${this.identifier} set default branch $DEFAULT_BRANCH")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeRepository::class.java)

        private const val LOCK_PREFIX = "Code-Repository-Lock-"

        // 锁最长持有时间
        private const val LOCK_AT_MOST_FOR = 60 * 60 * 1000L

        // 锁最短持有时间
        private const val LOCK_AT_LEAST_FOR = 0L
    }
}
