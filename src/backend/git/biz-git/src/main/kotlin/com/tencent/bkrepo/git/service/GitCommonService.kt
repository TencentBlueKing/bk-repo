package com.tencent.bkrepo.git.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.git.artifact.GitContentArtifactInfo
import com.tencent.bkrepo.git.artifact.GitRepositoryArtifactInfo
import com.tencent.bkrepo.git.constant.DOT_GIT
import com.tencent.bkrepo.git.constant.GitMessageCode
import com.tencent.bkrepo.git.constant.R_HEADS
import com.tencent.bkrepo.git.constant.R_REMOTE_ORIGIN
import com.tencent.bkrepo.git.util.FileUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.StreamUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Service
class GitCommonService {

    val logger: Logger = LoggerFactory.getLogger(GitCommonService::class.java)

    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var storageManager: StorageManager

    @Autowired
    lateinit var storageProperties: StorageProperties

    fun generateWorkDir(artifactContext: ArtifactContext): File {
        with(artifactContext) {
            val storageCredentials = storageCredentials ?: storageProperties.defaultStorageCredentials()
            val sha1 = "$projectId$repoName".sha1()
            val dirName = "${storageCredentials.upload.location}/${sha1.substring(0,2)}/${sha1.substring(2,40)}"
            val directory = File(dirName)
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("failed to create work directory ${directory.canonicalPath}")
            }
            logger.debug("create tmp git work dir ${directory.canonicalPath}")
            return directory
        }
    }

    /**
     * 创建git工作目录
     * */
    fun createGit(artifactContext: ArtifactContext, directory: File): Git {
        with(artifactContext) {
            if (directory.listFiles()?.isNotEmpty() == true)
                return Git(FileRepository(assembleGitDir(directory)))
            // 一般情况不会走到这里，除非服务器本地文件被清理了
            logger.info("acquire git file from storage")
            val nodeListOption = NodeListOption(
                pageNumber = 1,
                pageSize = 10000,
                includeFolder = false,
                includeMetadata = true,
                deep = true,
                sort = false
            )
            val response = nodeClient.listNodePage(projectId, repoName, DOT_GIT, nodeListOption)
            if (response.data == null || response.data!!.records.isEmpty()) {
                throw ErrorCodeException(GitMessageCode.GIT_REPO_NOT_SYNC)
            }
            return buildGit(response.data!!.records, directory, storageCredentials)
        }
    }

    /**
     * 根据存储文件，创建临时git目录
     * */
    fun buildGit(nodes: List<NodeInfo>, dir: File, storageCredentials: StorageCredentials?): Git {
        for (node in nodes) {
            val inputStream = storageManager.loadArtifactInputStream(NodeDetail(node), storageCredentials)
                ?: throw ErrorCodeException(GitMessageCode.GIT_ORIGINAL_FILE_MISS, node.fullPath)
            val file = File(dir.canonicalPath + node.fullPath)
            if (!file.parentFile.isDirectory && !file.parentFile.mkdirs()) {
                throw IOException("failed to create directory ${file.parentFile.canonicalPath}")
            }
            logger.debug("success create file ${file.canonicalPath}")
            inputStream.use { i ->
                FileOutputStream(file).use { o ->
                    StreamUtils.copy(i, o)
                }
            }
        }
        val gitDir = assembleGitDir(dir)
        return Git(FileRepository(gitDir))
    }

    /**
     * 拼接.git路径
     * */
    fun assembleGitDir(rootDir: File): String {
        return "${rootDir.canonicalPath}/$DOT_GIT"
    }

    /**
     * 存储.gir目录的文件
     * */
    fun storeGitDir(repository: Repository, context: ArtifactContext) {
        with(context) {
            val gitDir = repository.directory.canonicalPath
            logger.info("start store $gitDir")
            val filesToArchive: MutableList<File> = ArrayList()
            // 获取.git目录下所有的文件
            FileUtil.getAllFiles(File(gitDir), filesToArchive, true)

            for (file in filesToArchive) {
                val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(
                    file,
                    gitDir, this
                )
                storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
            }
            // 把远程分支更新到refs/heads,因为git clone默认取refs/heads的下的分支
            val remotes = Git(repository).branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
            for (remote in remotes) {
                val name = remote.name.replace(R_REMOTE_ORIGIN, R_HEADS)
                val updateRef = repository.updateRef(name)
                updateRef.setNewObjectId(remote.objectId)
                updateRef.forceUpdate()
                val rHeadFile = File("$gitDir/$name")
                val (artifactHeadFile, nodeCreateHeadRequest) = buildFileAndNodeCreateRequest(
                    rHeadFile,
                    gitDir, this
                )
                storageManager.storeArtifactFile(nodeCreateHeadRequest, artifactHeadFile, storageCredentials)
                logger.debug("success store head file ${rHeadFile.canonicalPath}")
            }
        }
    }

    /**
     * 检出文件
     * 先从缓存中查找文件，如果没有找到则从git中检出
     * */
    fun checkoutFileAndCreateNode(
        git: Git,
        gitContentArtifactInfo: GitContentArtifactInfo,
        context: ArtifactContext
    ): NodeDetail {
        with(context) {
            try {
                git.checkout()
                    .setStartPoint(gitContentArtifactInfo.objectId)
                    .addPath(gitContentArtifactInfo.path)
                    .call()
            } catch (e: Exception) {
                throw ErrorCodeException(
                    GitMessageCode.GIT_PATH_NOT_FOUND,
                    gitContentArtifactInfo.path!!, gitContentArtifactInfo.ref
                )
            }
            val workDir = git.repository.directory.parentFile.canonicalPath
            val filePath = "$workDir/${gitContentArtifactInfo.path}"
            val checkoutFile = File(filePath)
            if (!checkoutFile.exists()) {
                throw ErrorCodeException(
                    GitMessageCode.GIT_PATH_NOT_FOUND,
                    gitContentArtifactInfo.path!!, gitContentArtifactInfo.ref
                )
            }
            val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(checkoutFile, workDir, this)
            return storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, context.storageCredentials)
        }
    }

    fun buildFileAndNodeCreateRequest(
        file: File,
        workDir: String,
        context: ArtifactContext
    ): Pair<ArtifactFile, NodeCreateRequest> {
        with(context) {
            val artifactFile = file.toArtifactFile()
            val gitArtifactInfo = artifactInfo as GitRepositoryArtifactInfo
            // .git目录的文件，root path为src/.git，其他文件为src/。计算出文件的相对src的路径
            gitArtifactInfo.path = FileUtil.entryName(file, workDir)

            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                md5 = artifactFile.getFileMd5(),
                overwrite = true,
                operator = userId
            )
            return Pair(artifactFile, nodeCreateRequest)
        }
    }
}
