package com.tencent.bkrepo.git.service

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.git.artifact.GitPackFileArtifactInfo
import com.tencent.bkrepo.git.constant.BLANK
import com.tencent.bkrepo.git.context.DfsDataReadersHolder
import com.tencent.bkrepo.git.context.FileId
import com.tencent.bkrepo.git.context.UserHolder
import com.tencent.bkrepo.git.internal.storage.CodePackDescription
import com.tencent.bkrepo.git.internal.storage.CodeRepository
import com.tencent.bkrepo.git.internal.storage.DfsArtifactOutputStream
import com.tencent.bkrepo.git.internal.storage.DfsByteArrayDataReader
import com.tencent.bkrepo.git.internal.storage.DfsDataReader
import com.tencent.bkrepo.git.internal.storage.DfsFileDataReader
import com.tencent.bkrepo.git.internal.storage.DfsReadableChannel
import com.tencent.bkrepo.git.internal.storage.RepositoryDataService
import com.tencent.bkrepo.git.model.TDfsPackDescription
import com.tencent.bkrepo.git.repository.DfsPackDescriptionRepository
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel
import org.eclipse.jgit.internal.storage.pack.PackExt
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

@Service
class CodeRepositoryDataService(
    val storageManager: StorageManager,
    val nodeService: NodeService,
    val dfsPackDescriptionRepository: DfsPackDescriptionRepository
) : RepositoryDataService {

    override fun savePackDescriptions(
        repository: CodeRepository,
        desc: Collection<DfsPackDescription>,
        replace: Collection<DfsPackDescription>?
    ) {
        with(repository) {
            val uid = UserHolder.getUser()
            val saveList = desc.map {
                convert2TDfsPackDescription(
                    it as CodePackDescription,
                    projectId,
                    repoName,
                    uid
                )
            }.toList()
            dfsPackDescriptionRepository.insert(saveList)
        }
    }

    override fun deletePackDescriptions(repository: CodeRepository, desc: Collection<DfsPackDescription>) {
        with(repository) {
            val deleteList =
                desc.map {
                    deletePackFile(this, it as CodePackDescription)
                    it.packName
                }.toList()
            val query = Query(where(TDfsPackDescription::packName).`in`(deleteList))
            dfsPackDescriptionRepository.remove(query)
        }
    }

    override fun listPackDescriptions(repository: CodeRepository): List<DfsPackDescription> {
        with(repository) {
            val query = Query(
                where(TDfsPackDescription::projectId)
                    .isEqualTo(projectId)
                    .and(TDfsPackDescription::repoName.name).isEqualTo(repoName)
            )
            return dfsPackDescriptionRepository.find(query).map { convert2DfsBkCodePackDescription(it) }
        }
    }

    override fun getReadableChannel(repository: CodeRepository, fileName: String, blockSize: Int): ReadableChannel {
        with(repository) {
            val packArtifactInfo = GitPackFileArtifactInfo(projectId, repoName, BLANK, fileName)
            val fileId = FileId(projectId, repoName, fileName)
            val reader = DfsDataReadersHolder.getDfsReaders().getReader(fileId) ?: let {
                buildReader(repository, packArtifactInfo).apply {
                    DfsDataReadersHolder.getDfsReaders().putReader(fileId, this)
                }
            }
            return DfsReadableChannel(blockSize, reader)
        }
    }

    override fun getOutputStream(repository: CodeRepository, fileName: String): DfsOutputStream {
        val artifactFile = ArtifactFileFactory.buildDfsArtifactFile()
        return object : DfsArtifactOutputStream(artifactFile) {
            override fun doFlush() {
                with(repository) {
                    val packArtifactInfo = GitPackFileArtifactInfo(projectId, repoName, BLANK, fileName)
                    val nodeCreateRequest = NodeCreateRequest(
                        projectId = projectId,
                        repoName = repoName,
                        folder = false,
                        fullPath = packArtifactInfo.getArtifactFullPath(),
                        size = artifactFile.getSize(),
                        sha256 = artifactFile.getFileSha256(),
                        md5 = artifactFile.getFileMd5(),
                        operator = UserHolder.getUser()
                    )
                    storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
                }
            }
        }
    }

    private fun buildReader(
        repository: CodeRepository,
        packArtifactInfo: GitPackFileArtifactInfo
    ): DfsDataReader {
        with(repository) {
            val node = nodeService.getNodeDetail(
                ArtifactInfo(projectId, repoName, packArtifactInfo.getArtifactFullPath())
            ) ?: throw NodeNotFoundException(packArtifactInfo.getArtifactFullPath())
            val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
                ?: throw IllegalStateException("Stream load failed.")
            artifactInputStream.use {
                if (artifactInputStream is FileArtifactInputStream) {
                    return DfsFileDataReader(artifactInputStream.file)
                }
                val file = ArtifactFileFactory.build(artifactInputStream, node.size)
                file.getFile()?.let {
                    return DfsFileDataReader(it)
                }
                val output = ByteArrayOutputStream(node.size.toInt())
                file.getInputStream().use { it.copyTo(output) }
                return DfsByteArrayDataReader(output.toByteArray())
            }
        }
    }

    private fun convert2TDfsPackDescription(
        dfsPackDescription: CodePackDescription,
        projectId: String,
        repoName: String,
        uid: String
    ): TDfsPackDescription {
        with(dfsPackDescription) {
            return TDfsPackDescription(
                projectId = projectId,
                repoName = repoName,
                packName = packName,
                packSource = packSource.toString(),
                repoDesc = repositoryDescription.repositoryName,
                extensions = extensions,
                sizeMap = sizeMap.toJsonString(),
                blockSizeMap = blockSizeMap.toJsonString(),
                createdBy = uid,
                createdDate = LocalDateTime.now(),
                objectCount = objectCount,
                deltaCount = deltaCount,
                minUpdateIndex = minUpdateIndex,
                maxUpdateIndex = maxUpdateIndex,
                indexVersion = indexVersion,
                estimatedPackSize = estimatedPackSize
            )
        }
    }

    private fun convert2DfsBkCodePackDescription(dfsPackDescriptions: TDfsPackDescription):
        CodePackDescription {
        with(dfsPackDescriptions) {
            val codeDesc = CodePackDescription(
                repoDesc = DfsRepositoryDescription(repoDesc),
                packSource = DfsObjDatabase.PackSource.valueOf(packSource),
                packName = packName
            )
            codeDesc.extensions = extensions
            codeDesc.sizeMap = sizeMap.readJsonString()
            codeDesc.blockSizeMap = blockSizeMap.readJsonString()
            codeDesc.objectCount = objectCount
            codeDesc.deltaCount = deltaCount
            codeDesc.minUpdateIndex = minUpdateIndex
            codeDesc.maxUpdateIndex = maxUpdateIndex
            codeDesc.indexVersion = indexVersion
            codeDesc.estimatedPackSize = estimatedPackSize
            return codeDesc
        }
    }

    private fun deletePackFile(
        repository: CodeRepository,
        dfsBkCodePackDescription: CodePackDescription
    ) {
        val userId = UserHolder.getUser()
        PackExt.values().forEach {
            if (dfsBkCodePackDescription.hasFileExt(it)) {
                val projectId = repository.projectId
                val repoName = repository.repoName
                val fileName = dfsBkCodePackDescription.getFileName(it)
                val packArtifactInfo = GitPackFileArtifactInfo(projectId, repoName, BLANK, fileName)
                val deleteRequest = NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = packArtifactInfo.getArtifactFullPath(),
                    operator = userId
                )
                nodeService.deleteNode(deleteRequest)
            }
        }
    }
}
