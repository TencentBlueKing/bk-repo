package com.tencent.bkrepo.git.internal.storage

import com.tencent.bkrepo.common.api.constant.StringPool
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel
import org.eclipse.jgit.internal.storage.pack.PackExt
import org.slf4j.LoggerFactory

/**
 * git的对象存储数据库
 * */
class CodeObjDatabase(
    private val repository: CodeRepository,
    options: DfsReaderOptions,
    private val dataService: RepositoryDataService,
    private val blockSize: Int,
) : DfsObjDatabase(repository, options) {
    override fun newPack(source: PackSource): DfsPackDescription {
        val uniqueId = StringPool.uniqueId()
        return CodePackDescription(
            repository.description,
            "pack-$uniqueId-${source.name}", source
        )
    }

    override fun commitPackImpl(
        desc: MutableCollection<DfsPackDescription>,
        replaces: MutableCollection<DfsPackDescription>?
    ) {
        try {
            dataService.savePackDescriptions(repository, desc, replaces)
            clearCache()
        } catch (e: Exception) {
            logger.error("commit pack failed: ", e)
            throw e
        }
    }

    override fun rollbackPack(desc: MutableCollection<DfsPackDescription>) {
        try {
            dataService.deletePackDescriptions(repository, desc)
        } catch (e: Exception) {
            logger.error("rollback pack failed: ", e)
            throw e
        }
    }

    override fun listPacks(): List<DfsPackDescription> {
        try {
            return dataService.listPackDescriptions(repository)
        } catch (e: Exception) {
            logger.error("list pack failed: ", e)
            throw e
        }
    }

    override fun openFile(desc: DfsPackDescription, ext: PackExt): ReadableChannel {
        try {
            return dataService.getReadableChannel(
                repository,
                desc.getFileName(ext),
                blockSize
            )
        } catch (e: Exception) {
            logger.error("open file failed: ", e)
            throw e
        }
    }

    override fun writeFile(desc: DfsPackDescription, ext: PackExt): DfsOutputStream {
        try {
            return dataService.getOutputStream(repository, desc.getFileName(ext))
        } catch (e: Exception) {
            logger.error("write file failed: ", e)
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeObjDatabase::class.java)
    }
}
