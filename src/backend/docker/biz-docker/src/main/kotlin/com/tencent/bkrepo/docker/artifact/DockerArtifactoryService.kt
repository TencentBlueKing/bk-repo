package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.DataDigestUtils
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.context.WriteContext
import com.tencent.bkrepo.docker.exception.DockerFileReadFailedException
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerMoveFileFailedException
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DockerArtifactoryService @Autowired constructor(
    val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val fileStorage: FileStorage,
    private val metadataService: MetadataResource

) {

    protected lateinit var context: DockerWorkContext

    @Value("\${storage.localTempPath}")
    private var localPath: String = ""

    lateinit var userId: String

    init {
        this.context = DockerWorkContext()
    }

    fun writeLocal(projectId: String, repoName: String, dockerRepo: String, name: String, inputStream: InputStream): ResponseEntity<Any> {

        val filePath = "$localPath/$projectId/$repoName/$dockerRepo/"
        var fullPath = "/$localPath/$projectId/$repoName/$dockerRepo/$name"
        File(filePath).mkdirs()
        val file = File(fullPath)
        if (!file.exists()) {
            file.createNewFile()
        }
        val outputStream = FileOutputStream(file, true)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return ResponseEntity.ok().body("ok")
    }

    fun readLocal(projectId: String, repoName: String, name: String): InputStream {
        // check repository
        repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId] read local  [$name] failed: [$projectId,$repoName] not found")
            throw DockerRepoNotFoundException(repoName)
        }
        var fullPath = "$localPath/$projectId/$repoName/$name"
        return File(fullPath).inputStream()
    }

    fun readGlobal(context: DownloadContext): InputStream {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId] read local  [${context.name}] failed: [${context.projectId},${context.repoName}] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        // get content from storage
        val file = fileStorage.load(context.sha256, repository.storageCredentials) ?: kotlin.run {
            throw DockerFileReadFailedException(context.repoName)
        }
        return file.inputStream()
    }

    fun getWorkContextC(): DockerWorkContext {
        return this.context
    }

    fun write(context: WriteContext) {
        // check the repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  write   [$context.path] failed: [${context.repoName}] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }

        // save the node
        val result = nodeResource.create(NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.path,
            size = context.contentLength,
            sha256 = context.sha256,
            operator = userId
        ))

        if (result.isOk()) {
            fileStorage.store(context.sha256, context.content!!, repository.storageCredentials)
            logger.info("user[$context.userId] write file [$context.path] success")
        } else {
            logger.warn("user[$context.userId] write file [$context.path] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.path)
        }
    }

    fun delete(path: String): Boolean {
        return true
    }

    // delete local not need to check repository
    fun deleteLocal(projectId: String, repoName: String, name: String): Boolean {
        val fullPath = "$localPath/$projectId/$repoName/$name"
        return File(fullPath).delete()
    }

    // download file not need to check repository
    fun download(context: DownloadContext): File {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId] simply download file  [$context.path] failed: [$context.repoName] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }

        // fileStorage
        var file = fileStorage.load(context.sha256, repository.storageCredentials)
        return file!!
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun upload(context: UploadContext): ResponseEntity<Any> {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  upload file  [$context.path] failed: ${context.repoName} not found")
            throw DockerRepoNotFoundException(context.repoName)
        }

        // save node
        val result = nodeResource.create(NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.path,
            size = context.contentLength,
            sha256 = context.sha256,
            operator = userId,
            metadata = emptyMap(),
            overwrite = true
        ))

        if (result.isOk()) {
            fileStorage.store(context.sha256, context.content!!, repository.storageCredentials)
            logger.info("user[$userId]  upload file [$context.path] success")
        } else {
            logger.warn("user[$userId]  upload file [$context.path] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.path)
        }
        return ResponseEntity.ok().body("ok")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun uploadFromLocal(path: String, context: UploadContext): ResponseEntity<Any> {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  upload file  [$context.path] failed: ${context.repoName} not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        var fullPath = "$localPath/${context.projectId}/${context.repoName}/$path"
        var content = File(fullPath).readBytes()
        context.content(content.inputStream()).contentLength(content.size.toLong()).sha256(DataDigestUtils.sha256FromByteArray(content))

        val node = NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.path,
            size = context.contentLength,
            sha256 = context.sha256,
            operator = userId,
            metadata = emptyMap(),
            overwrite = true
        )

        // save node
        val result = nodeResource.create(node)
        if (result.isOk()) {
            fileStorage.store(context.sha256, context.content!!, repository.storageCredentials)
            logger.info("user[$userId] upload file from local [$context.path] success")
        } else {
            logger.warn("user[$userId] upload file from local  [$context.path] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.path)
        }
        return ResponseEntity.ok().body("ok")
    }

    fun copy(projectId: String, repoName: String, srcPath: String, destPath: String): Boolean {
        val copyRequest = NodeCopyRequest(
            srcProjectId = projectId,
            srcRepoName = repoName,
            srcFullPath = srcPath,
            destProjectId = projectId,
            destRepoName = repoName,
            destPath = destPath,
            overwrite = true,
            operator = userId
        )
        nodeResource.copy(copyRequest)
        return true
    }

    fun move(projectId: String, repoName: String, from: String, to: String): Boolean {
        val renameRequest = NodeRenameRequest(projectId, repoName, from, to, userId)
        val result = nodeResource.rename(renameRequest)
        if (result.isNotOk()) {
            logger.warn("user[$userId] rename  [$from] to [$to] failed: [${result.code}, ${result.message}]")
            throw DockerMoveFileFailedException(from + "->" + to)
        }
        return true
    }

    fun setAttributes(projectId: String, repoName: String, path: String, keyValueMap: Map<String, String>) {
        metadataService.save(MetadataSaveRequest(projectId, repoName, path, keyValueMap))
    }

    fun getAttribute(projectId: String, repoName: String, fullPath: String, key: String): String? {
        return metadataService.query(projectId, repoName, fullPath).data!!.get(key)
    }

    fun exists(projectId: String, repoName: String, dockerRepo: String): Boolean {
        return nodeResource.exist(projectId, repoName, dockerRepo).data!!
    }

    fun existsLocal(projectId: String, repoName: String, path: String): Boolean {
        val fullPath = "$localPath/$projectId/$repoName/$path"
        val file = File(fullPath)
        return file.exists()
    }

    fun canRead(path: String): Boolean {
        return true
    }

    fun canWrite(path: String): Boolean {
        return true
    }

    fun canDelete(path: String): Boolean {
        return true
    }

    fun artifactLocal(projectId: String, repoName: String, dockerRepo: String): Artifact? {
        val fullPath = "$localPath/$projectId/$repoName/$dockerRepo"
        val file = File(fullPath)
        val content = file.readBytes()
        val sha256 = DataDigestUtils.sha256FromByteArray(content)
        var length = content.size.toLong()
        return Artifact(projectId, repoName, dockerRepo).sha256(sha256).contentLength(length)
    }

    fun artifact(projectId: String, repoName: String, fullPath: String): Artifact? {
        val nodes = nodeResource.detail(projectId, repoName, fullPath).data ?: run {
            logger.warn("find artifact failed: $projectId, $repoName, $fullPath found no artifacts")
            return null
        }
        return Artifact(projectId, repoName, fullPath).sha256(nodes.nodeInfo.sha256!!).contentLength(nodes.nodeInfo.size)
    }

    fun findArtifact(projectId: String, repoName: String, dockerRepo: String, fileName: String): NodeDetail? {
        // query node info
        var fullPath = "/$dockerRepo/$fileName"
        val nodes = nodeResource.detail(projectId, repoName, fullPath).data ?: run {
            logger.warn("find artifacts failed: $projectId, $repoName, $fullPath found no node")
            return null
        }
        return nodes
    }

    fun findArtifacts(projectId: String, repoName: String, fileName: String): List<Map<String, Any>> {
        val projectRule = Rule.QueryRule("projectId", projectId)
        val repoNameRule = Rule.QueryRule("repoName", repoName)
        val nameRule = Rule.QueryRule("name", fileName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("fullPath", "path", "size"),
            rule = rule
        )

        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find artifacts failed: [$projectId, $repoName, $fileName] found no node")
            return emptyList()
        }
        return result.records
    }

    fun findArtifactsByName(projectId: String, repoName: String, fileName: String): List<Map<String, Any>> {
        // find artifacts by name
        val projectRule = Rule.QueryRule("projectId", projectId)
        val repoNameRule = Rule.QueryRule("repoName", repoName)
        val nameRule = Rule.QueryRule("name", fileName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(0, 9999999),
            sort = Sort(listOf("path"), Sort.Direction.ASC),
            select = mutableListOf("path"),
            rule = rule
        )
        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find artifacts failed:  $fileName found no node")
            return emptyList()
        }
        return result.records
    }

    fun findManifest(projectId: String, repoName: String, manifestPath: String): NodeDetail? {
        // query node info
        val nodes = nodeResource.detail(projectId, repoName, manifestPath).data ?: run {
            logger.warn("find manifest failed: $projectId, $repoName, $manifestPath found no node")
            return null
        }
        return nodes
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactoryService::class.java)
    }
}
