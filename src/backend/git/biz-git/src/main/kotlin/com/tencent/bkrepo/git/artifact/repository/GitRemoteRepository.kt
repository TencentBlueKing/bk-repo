/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.git.artifact.repository

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.git.artifact.GitContentArtifactInfo
import com.tencent.bkrepo.git.artifact.GitRepositoryArtifactInfo
import com.tencent.bkrepo.git.constant.CACHE_REF_PATH
import com.tencent.bkrepo.git.constant.GitMessageCode
import com.tencent.bkrepo.git.constant.OBJECT_ID
import com.tencent.bkrepo.git.constant.R_REMOTE_ORIGIN
import com.tencent.bkrepo.git.constant.DOT_GIT
import com.tencent.bkrepo.git.constant.REDIS_SET_REPO_TO_UPDATE
import com.tencent.bkrepo.git.util.FileUtil
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.common.redis.RedisOperation
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import org.springframework.util.StringUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Component
class GitRemoteRepository : RemoteRepository() {

    val logger: Logger = LoggerFactory.getLogger(GitRemoteRepository::class.java)

    @Autowired
    lateinit var storageProperties: StorageProperties

    @Autowired
    lateinit var redisOperation: RedisOperation

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val gitContentArtifactInfo = artifactInfo as GitContentArtifactInfo
            val ref = gitContentArtifactInfo.ref
            var directory: File? = null
            var git: Git? = null
            var gitInit = false
            if (ObjectId.isId(ref)) {
                // commit id
                gitContentArtifactInfo.objectId = ref
            } else {
                directory = createTmpdir()
                // 查询tag or branch 的object id cache
                git = createGit(directory)
                gitInit = true
                gitContentArtifactInfo.objectId = resolverRefAndCache(
                    gitContentArtifactInfo,
                    git, ref
                )
            }
            val node = nodeClient.getNodeDetail(
                projectId, repoName,
                artifactInfo.getArtifactFullPath()
            ).data ?: let {
                if (!gitInit) {
                    directory = createTmpdir()
                    git = createGit(directory!!)
                }
                checkoutFileAndCreateNode(git!!, gitContentArtifactInfo, context, directory!!)
            }
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()
            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.PROXY, useDisposition)
        }
    }

    /**
     * 获取Ref的objectId
     * 先从缓存中读取，如果没有读取到，
     * 则从git中检索出来并且缓存到node，方便下次使用
     * */
    private fun ArtifactDownloadContext.resolverRefAndCache(
        gitContentArtifactInfo: GitContentArtifactInfo,
        git: Git,
        ref: String
    ): String {
        return nodeClient.getNodeDetail(
            projectId, repoName,
            "/${CACHE_REF_PATH}${gitContentArtifactInfo.ref}"
        ).data?.metadata?.get(OBJECT_ID)?.toString() ?: let {
            // 没有查询到，则加载git目录，并尝试解析ref
            // tag or branch
            val objectId = resolverRef(git, gitContentArtifactInfo, ref)
            cacheRef(this, objectId.name)
            gitContentArtifactInfo.objectId = objectId.name
            objectId.name
        }
    }

    /**
     * 检出文件
     * 先从缓存中查找文件，如果没有找到则从git中检出
     * */
    private fun ArtifactDownloadContext.checkoutFileAndCreateNode(
        git: Git,
        gitContentArtifactInfo: GitContentArtifactInfo,
        context: ArtifactDownloadContext,
        directory: File
    ): NodeDetail {
        val gitDir = assembleGitDir(directory)
        git.checkout()
            .setStartPoint(gitContentArtifactInfo.objectId)
            .addPath(gitContentArtifactInfo.path)
            .call()

        val filePath = "${directory.canonicalPath}/${gitContentArtifactInfo.path}"
        val checkoutFile = File(filePath)
        if (!checkoutFile.exists()) {
            throw ErrorCodeException(
                GitMessageCode.GIT_PATH_NOT_FOUND,
                gitContentArtifactInfo.path!!, gitContentArtifactInfo.ref
            )
        }
        val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(checkoutFile, false, gitDir)
        return storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, context.storageCredentials)
    }

    /**
     * 解析ref为objectId
     * */
    private fun resolverRef(git: Git, gitContentArtifactInfo: GitContentArtifactInfo, ref: String): ObjectId {
        return git.repository
            .resolve("${R_REMOTE_ORIGIN}${gitContentArtifactInfo.ref}") ?: let {
            git.repository.resolve(gitContentArtifactInfo.ref) ?: let {
                throw ErrorCodeException(GitMessageCode.GIT_REF_NOT_FOUND, ref)
            }
        }
    }

    /**
     * 缓存ref
     * */
    private fun cacheRef(context: ArtifactDownloadContext, oid: String) {
        val info = context.artifactInfo as GitContentArtifactInfo
        val cacheNode = NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = true,
            fullPath = ".ref/${info.ref}",
            overwrite = true,
            operator = context.userId,
            metadata = mapOf(OBJECT_ID to oid)
        )
        nodeClient.createNode(cacheNode)
    }

    /**
     * 创建git工作目录
     * */
    private fun ArtifactDownloadContext.createGit(directory: File): Git {
        if (directory.listFiles()?.isNotEmpty() == true)
            return Git(FileRepository(assembleGitDir(directory)))
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

    /**
     * 根据存储文件，创建临时git目录
     * */
    private fun buildGit(nodes: List<NodeInfo>, dir: File, storageCredentials: StorageCredentials?): Git {
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
    private fun assembleGitDir(rootDir: File): String {
        return "${rootDir.canonicalPath}/$DOT_GIT"
    }

    private fun ArtifactDownloadContext.createTmpdir(): File {
        val storageCredentials = storageCredentials ?: storageProperties.defaultStorageCredentials()
        val directory = File(storageCredentials.upload.location)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("failed to create tmp directory ${directory.canonicalPath}")
        }
        // name hash 多级目录
        val sha1 = "$projectId$repoName".sha1()
        val dir = File(File(directory, sha1.substring(0, 2)), sha1)
        if (!directory.isDirectory && !dir.mkdir())
            throw throw IOException("failed to create tmp work directory ${dir.canonicalPath}")

        logger.debug("create tmp git work dir ${dir.canonicalPath}")
        return dir
    }

    /**
     * 同步仓库
     * */
    fun sync(context: ArtifactDownloadContext) {
        logger.debug("start sync project ${context.projectId} repo ${context.repoName}")
        this.onDownloadBefore(context)
        with(context) {
            val remoteConfiguration = getRemoteConfiguration()
            val directory = createTmpdir()
            var credentialsProvider: CredentialsProvider? = null
            if (remoteConfiguration.credentials.username != null &&
                remoteConfiguration.credentials.password != null
            ) {
                credentialsProvider = UsernamePasswordCredentialsProvider(
                    remoteConfiguration.credentials.username, remoteConfiguration.credentials.password
                )
            }
            // 检查是否存在.git目录
            if (nodeClient.checkExist(projectId, repoName, artifactInfo.getArtifactFullPath()).data!!) {
                updateRepo(directory, credentialsProvider, null)
            } else {
                // 克隆仓库
                if (StringUtils.isEmpty(remoteConfiguration.url)) {
                    throw ErrorCodeException(GitMessageCode.GIT_URL_NOT_CONFIG)
                }
                logger.debug("start clone ${remoteConfiguration.url}")
                val git = clone(directory, credentialsProvider, remoteConfiguration.url)
                logger.debug("end clone ${remoteConfiguration.url}")
                val toUpdate = redisOperation
                    .getSetMembers(REDIS_SET_REPO_TO_UPDATE)?.contains(artifactInfo.getArtifactName())
                if (toUpdate == true)
                    updateRepo(directory, credentialsProvider, git)
                else
                    storeGitDir("${directory.canonicalPath}/$DOT_GIT")
            }
        }
    }

    private fun ArtifactDownloadContext.updateRepo(
        directory: File,
        credentialsProvider: CredentialsProvider?,
        gitv: Git?
    ) {
        // 更新仓库,拉取新的数据，并且上传。完成后清理掉原来的ref缓存
        val git = gitv ?: createGit(directory)
        val fetchCommand = git.fetch()
            .setTagOpt(TagOpt.FETCH_TAGS)
        credentialsProvider.let {
            fetchCommand.setCredentialsProvider(credentialsProvider)
        }
        do {
            logger.debug("start fetch ${artifactInfo.getArtifactName()}")
            fetchCommand.call()
            logger.debug("end fetch ${artifactInfo.getArtifactName()}")
            val toUpdate = redisOperation
                .getSetMembers(REDIS_SET_REPO_TO_UPDATE)?.contains(artifactInfo.getArtifactName())
            if (toUpdate != true) {
                storeGitDir(git.repository.directory.canonicalPath)
                logger.debug("update ${artifactInfo.getArtifactName()}   state: ${git.repository.repositoryState}")
                // 清理ref缓存
                cleanRefs(this)
                return
            }
            // 为防止频繁的触发更新，每隔3s轮训一次
            logger.debug("check toUpdate OK : ${artifactInfo.getArtifactName()} 3s later fetch")
            redisOperation.sremove(REDIS_SET_REPO_TO_UPDATE, artifactInfo.getArtifactName())
            logger.debug("remove $REDIS_SET_REPO_TO_UPDATE ${artifactInfo.getArtifactName()}")
            TimeUnit.SECONDS.sleep(3)
        } while (true)
    }

    /**
     * 清理ref缓存
     * */
    private fun cleanRefs(context: ArtifactDownloadContext) {
        val nodeDeleteRequest = NodeDeleteRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            fullPath = CACHE_REF_PATH,
            operator = context.userId
        )
        nodeClient.deleteNode(nodeDeleteRequest)
    }

    /**
     * 存储.gir目录的文件
     * */
    private fun ArtifactDownloadContext.storeGitDir(gitDir: String) {
        logger.debug("start store $gitDir")
        val filesToArchive: MutableList<File> = ArrayList()
        // 获取.git目录下所有的文件
        FileUtil.getAllFiles(File(gitDir), filesToArchive, true)
        for (file in filesToArchive) {
            val (artifactFile, nodeCreateRequest) = buildFileAndNodeCreateRequest(file, true, gitDir)
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
        }
    }

    private fun ArtifactDownloadContext.buildFileAndNodeCreateRequest(
        file: File,
        isDotGitFile: Boolean,
        gitDir: String
    ): Pair<ArtifactFile, NodeCreateRequest> {
        val artifactFile = file.toArtifactFile()
        val gitArtifactInfo = artifactInfo as GitRepositoryArtifactInfo
        // .git目录的文件，root path为src/.git，其他文件为src/。计算出文件的相对src的路径
        gitArtifactInfo.path = FileUtil.entryName(
            file,
            if (isDotGitFile)
                gitDir else gitDir.removeSuffix("/$DOT_GIT")
        )

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

    /**
     * 克隆仓库
     * */
    private fun clone(directory: File, credentialsProvider: CredentialsProvider?, url: String): Git {
        val command = Git.cloneRepository()
        command.setDirectory(directory)
        command.setURI(url)
        command.setNoCheckout(true)
        credentialsProvider.let {
            command.setCredentialsProvider(credentialsProvider)
        }

        return command.call()
    }
}
