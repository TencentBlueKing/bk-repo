package com.tencent.bkrepo.git.internal.storage

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import net.javacrumbs.shedlock.core.LockProvider
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription

/**
 * CodeRepository构建器
 * */
class CodeRepositoryBuilder(
    val projectId: String,
    val repoName: String,
    val storageCredentials: StorageCredentials?,
    val dataService: RepositoryDataService,
    val blockSize: Int,
    val lockProvider: LockProvider
) : DfsRepositoryBuilder<CodeRepositoryBuilder, CodeRepository>() {

    override fun build(): CodeRepository {
        repositoryDescription ?: let { repositoryDescription = DfsRepositoryDescription() }
        readerOptions ?: let { readerOptions = DfsReaderOptions() }
        return CodeRepository(
            projectId = projectId,
            repoName = repoName,
            storageCredentials = storageCredentials,
            dataService = dataService,
            blockSize = blockSize,
            builder = this,
            lockProvider = lockProvider
        )
    }
}
