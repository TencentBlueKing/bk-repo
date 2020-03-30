package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.artifact.permission.PermissionService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.exception.DockerFileReadFailedException
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerMoveFileFailedException
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import com.tencent.bkrepo.docker.model.DockerBasicPath
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import java.io.File
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class DockerArtifactoryService @Autowired constructor(
    val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val storageService: StorageService,
    private val metadataService: MetadataResource,
    private val permissionService: PermissionService
) {

    protected lateinit var context: DockerWorkContext

    lateinit var userId: String

    init {
        this.context = DockerWorkContext()
    }

    fun startAppend(): String {
        return storageService.createAppendId()
    }

    fun writeAppend(uuid: String, artifactFile: ArtifactFile): Long {
        val result = this.storageService.append(uuid, artifactFile)
        return result
    }

    fun readGlobal(context: DownloadContext): InputStream {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId] read local  [${context.name}] failed: [${context.projectId},${context.repoName}] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        // get content from storage
        val file = storageService.load(context.sha256, repository.storageCredentials) ?: kotlin.run {
            throw DockerFileReadFailedException(context.repoName)
        }
        logger.info("load file sha256 {}, length {}", context.sha256, file.length())
        return file.inputStream()
    }

    fun getWorkContextC(): DockerWorkContext {
        return this.context
    }

    fun delete(path: String): Boolean {
        return true
    }

    // download file
    fun download(context: DownloadContext): File {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file  [$context.path] failed: [$context.repoName] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        // load file from storage
        var file = storageService.load(context.sha256, repository.storageCredentials) ?: run {
            logger.warn("user [$userId]  download file  [$context.path] failed: [$context.repoName] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        return file
    }

    // upload file
    fun upload(context: UploadContext): ResponseEntity<Any> {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  upload file  [$context.path] failed: [${context.repoName}] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }

        // save the node
        val result = nodeResource.create(
            NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.path,
                size = context.contentLength,
                sha256 = FileDigestUtils.fileSha256(context.artifactFile!!.getInputStream()),
                md5 = FileDigestUtils.fileMd5(context.artifactFile!!.getInputStream()),
                operator = userId,
                metadata = emptyMap(),
                overwrite = true
            )
        )
        if (result.isOk()) {
            storageService.store(context.sha256, context.artifactFile!!, repository.storageCredentials)
            logger.info("user[$userId]  upload file [$context.path] success")
        } else {
            logger.warn("user[$userId]  upload file [$context.path] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.path)
        }
        return ResponseEntity.ok().body("ok")
    }

    // finish append file
    fun finishAppend(uuid: String, context: UploadContext): ResponseEntity<Any> {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  finish append file  [$context.path] failed: ${context.repoName} not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        val file = this.storageService.finishAppend(uuid, repository.storageCredentials)
        val node = NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.path,
            size = file.size,
            sha256 = file.sha256,
            md5 = file.md5,
            operator = userId,
            metadata = emptyMap(),
            overwrite = true
        )
        // save node
        val result = nodeResource.create(node)
        if (!result.isOk()) {
            logger.error("user [$userId] finish upload file  [$context.path] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.path)
        }
        logger.info("user [$userId] finish upload file  {} , {} success", context.path, file.sha256)
        return ResponseEntity.ok().body("ok")
    }

    // copy file
    fun copy(projectId: String, repoName: String, srcPath: String, destPath: String): Boolean {
        val copyRequest = NodeCopyRequest(
            srcProjectId = projectId,
            srcRepoName = repoName,
            srcFullPath = srcPath,
            destProjectId = projectId,
            destRepoName = repoName,
            destFullPath = destPath,
            overwrite = true,
            operator = userId
        )
        nodeResource.copy(copyRequest)
        return true
    }

    // move file
    fun move(projectId: String, repoName: String, from: String, to: String): Boolean {
        val renameRequest = NodeRenameRequest(projectId, repoName, from, to, userId)
        logger.info("rename request {}", renameRequest.toString())
        val result = nodeResource.rename(renameRequest)
        if (result.isNotOk()) {
            logger.error("user [$userId] rename  [$from] to [$to] failed: [${result.code}, ${result.message}]")
            throw DockerMoveFileFailedException(from + "->" + to)
        }
        return true
    }

    // set node  attribute
    fun setAttributes(projectId: String, repoName: String, path: String, keyValueMap: Map<String, String>) {
        metadataService.save(MetadataSaveRequest(projectId, repoName, path, keyValueMap))
    }

    // get node  attribute
    fun getAttribute(projectId: String, repoName: String, fullPath: String, key: String): String? {
        logger.info("getAttribute params :{}, {}, {}, {}", projectId, repoName, fullPath, key)
        val result = metadataService.query(projectId, repoName, fullPath).data!!
        logger.info("getAttribute result  :{}", result.toString())
        return result.get(key)
    }

    // check node
    fun exists(projectId: String, repoName: String, dockerRepo: String): Boolean {
        val result = nodeResource.exist(projectId, repoName, dockerRepo).data ?: return false
        return result
    }

    // check path read permission
    fun canRead(path: DockerBasicPath): Boolean {
        try {
            permissionService.checkPermission(
                userId,
                ResourceType.PROJECT,
                PermissionAction.WRITE,
                path.projectId,
                path.repoName
            )
            return true
        } catch (e: PermissionCheckException) {
            logger.error("user: {} ,check read permission fail {},{}", userId, path.projectId, path.repoName)
            return false
        }
    }

    // check path write permission
    fun canWrite(path: DockerBasicPath): Boolean {
        try {
            permissionService.checkPermission(
                userId,
                ResourceType.PROJECT,
                PermissionAction.WRITE,
                path.projectId,
                path.repoName
            )
            return true
        } catch (e: PermissionCheckException) {
            logger.error("user: {} ,check write permission fail {},{}", userId, path.projectId, path.repoName)
            return false
        }
    }

    // construct artifact object
    fun artifact(projectId: String, repoName: String, fullPath: String): Artifact? {
        val nodes = nodeResource.detail(projectId, repoName, fullPath).data ?: run {
            logger.error("get  artifact detail failed: $projectId, $repoName, $fullPath found no artifact")
            return null
        }
        if (nodes.nodeInfo.sha256 == null) {
            logger.error("get  artifact detail failed: $projectId, $repoName, $fullPath found no artifact")
            return null
        }
        return Artifact(projectId, repoName, fullPath).sha256(nodes.nodeInfo.sha256!!)
            .contentLength(nodes.nodeInfo.size)
    }

    // find artifact
    fun findArtifact(path: DockerBasicPath, fileName: String): NodeDetail? {
        // get node info
        var fullPath = "/${path.dockerRepo}/$fileName"
        val nodes = nodeResource.detail(path.projectId, path.repoName, fullPath).data ?: run {
            logger.warn("get  artifact detail failed: ${path.projectId}, ${path.repoName}, $fullPath found no node")
            return null
        }
        return nodes
    }

    // find artifact list
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
            logger.warn("find artifact list failed: [$projectId, $repoName, $fileName] found no node")
            return emptyList()
        }
        return result.records
    }

    // find repo list
    fun findRepoList(projectId: String, repoName: String): List<String> {
        val projectRule = Rule.QueryRule("projectId", projectId)
        val repoNameRule = Rule.QueryRule("repoName", repoName)
        val nameRule = Rule.QueryRule("name", "manifest.json")
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(0, 10000),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("fullPath", "path", "size"),
            rule = rule
        )

        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find repo list failed: [$projectId, $repoName] ")
            return emptyList()
        }
        var data = mutableListOf<String>()
        result.records.forEach {
            var path = it.get("path") as String
            data.add(path.removeSuffix("/").replaceAfterLast("/", "").removeSuffix("/").removePrefix("/"))
        }
        return data.distinct()
    }

    // find repo tag list
    fun findRepoTagList(projectId: String, repoName: String, image: String): Map<String, String> {
        val projectRule = Rule.QueryRule("projectId", projectId)
        val repoNameRule = Rule.QueryRule("repoName", repoName)
        val nameRule = Rule.QueryRule("name", "manifest.json")
        val pathRule = Rule.QueryRule("path", "/$image/", OperationType.PREFIX)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule, pathRule))
        val queryModel = QueryModel(
            page = PageLimit(0, 100000),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("fullPath", "path", "size", "createdBy"),
            rule = rule
        )

        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find artifacts failed: [$projectId, $repoName] found no node")
            return emptyMap()
        }
        var data = mutableMapOf<String, String>()
        result.records.forEach {
            var path = it.get("path") as String
            val tag = path.removePrefix("/$image/").removeSuffix("/")
            val user = it.get("createdBy") as String
            data.put(tag, user)
        }
        return data
    }

    // find artifacts by name
    fun findArtifactsByName(projectId: String, repoName: String, fileName: String): List<Map<String, Any>> {
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
            logger.error("find artifacts failed:  $fileName found no node")
            return emptyList()
        }
        return result.records
    }

    // find manifest
    fun findManifest(projectId: String, repoName: String, manifestPath: String): NodeDetail? {
        // query node info
        val nodes = nodeResource.detail(projectId, repoName, manifestPath).data ?: run {
            logger.error("find manifest failed: $projectId, $repoName, $manifestPath found no node")
            return null
        }
        return nodes
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactoryService::class.java)
    }
}
