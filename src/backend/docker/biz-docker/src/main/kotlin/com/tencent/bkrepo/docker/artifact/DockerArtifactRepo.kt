package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.PermissionCheckException
import com.tencent.bkrepo.common.artifact.permission.PermissionService
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.docker.constant.DOCKER_CREATE_BY
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_FULL_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_SIZE
import com.tencent.bkrepo.docker.constant.DOCKER_PRE_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_PROJECT_ID
import com.tencent.bkrepo.docker.constant.DOCKER_REPO_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_SEARCH_INDEX
import com.tencent.bkrepo.docker.constant.DOCKER_SEARCH_LIMIT
import com.tencent.bkrepo.docker.constant.DOCKER_SEARCH_LIMIT_SMALL
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.exception.DockerFileReadFailedException
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerMoveFileFailedException
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream

/*
 * docker repo storage interface
 * to work with storage module
 * @author: owenlxu
 * @date: 2020-06-10
 */
@Service
class DockerArtifactRepo @Autowired constructor(
    val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val storageService: StorageService,
    private val metadataService: MetadataResource,
    private val permissionService: PermissionService
) {

    lateinit var userId: String

    fun startAppend(context: RequestContext): String {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("user [$userId] start to append file ")
        return storageService.createAppendId(repository.storageCredentials)
    }

    fun writeAppend(uuid: String, artifactFile: ArtifactFile, context: RequestContext): Long {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("user [$userId]  append file id [$uuid]")
        return this.storageService.append(uuid, artifactFile, repository.storageCredentials)
    }

    // TODO : to implement
    fun delete(path: String): Boolean {
        return true
    }

    // download file
    fun download(downloadContext: DownloadContext): InputStream {
        // check repository
        val context = downloadContext.context
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("load file sha256 [${downloadContext.sha256}], length [${downloadContext.length}]")
        // load file from storage
        return storageService.load(downloadContext.sha256, Range.ofFull(downloadContext.length), repository.storageCredentials) ?: run {
            logger.error("user [$userId] fail to load data [$downloadContext] from storage  ")
            throw DockerFileReadFailedException(context.repoName)
        }
    }

    // upload file
    fun upload(context: UploadContext): Boolean {
        // check repository
        val repository = repositoryResource.detail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  upload file  [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }

        logger.debug("user [$userId] start to store file [${context.sha256}]")
        // store the file
        storageService.store(context.sha256, context.artifactFile!!, repository.storageCredentials)
        // save the node
        val result = nodeResource.create(
            NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.fullPath,
                size = context.artifactFile!!.getSize(),
                sha256 = context.artifactFile!!.getFileSha256(),
                md5 = context.artifactFile!!.getFileMd5(),
                operator = userId,
                metadata = emptyMap(),
                overwrite = true
            )
        )
        if (result.isNotOk()) {
            logger.error("user [$userId]  upload file [${context.fullPath}] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.fullPath)
        }
        logger.debug("user [$userId]  upload file [${context.fullPath}] success")
        return true
    }

    // finish append file
    fun finishAppend(uuid: String, context: UploadContext): Boolean {
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
            fullPath = context.fullPath,
            size = file.size,
            sha256 = file.sha256,
            md5 = file.md5,
            operator = userId,
            metadata = emptyMap(),
            overwrite = true
        )
        // save node
        val result = nodeResource.create(node)
        if (result.isNotOk()) {
            logger.error("user [$userId] finish upload file  [${context.fullPath}] failed: [${result.code}, ${result.message}]")
            throw DockerFileSaveFailedException(context.fullPath)
        }
        logger.debug("user [$userId] finish append file  [${context.fullPath} , ${file.sha256}] success")
        return true
    }

    // copy file
    fun copy(context: RequestContext, srcPath: String, destPath: String): Boolean {
        with(context) {
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
            val result = nodeResource.copy(copyRequest)
            if (result.isNotOk()) {
                logger.error("user [$userId] request [$copyRequest] copy file fail")
                throw DockerMoveFileFailedException("$srcPath->$destPath")
            }
            return true
        }
    }

    // move file
    fun move(context: RequestContext, from: String, to: String): Boolean {
        with(context) {
            val renameRequest = NodeRenameRequest(projectId, repoName, from, to, userId)
            logger.debug("rename request [$renameRequest]")
            val result = nodeResource.rename(renameRequest)
            if (result.isNotOk()) {
                logger.error("user [$userId] request [$renameRequest] rename file fail")
                throw DockerMoveFileFailedException("$from->$to")
            }
            return true
        }
    }

    // set node attribute
    fun setAttributes(projectId: String, repoName: String, path: String, data: Map<String, String>) {
        logger.info("set attributes request [$projectId,$repoName,$path,$data]")
        val result = metadataService.save(MetadataSaveRequest(projectId, repoName, path, data))
        if (result.isNotOk()) {
            logger.error("set attribute [$projectId,$repoName,$path,$data] fail")
            throw DockerFileSaveFailedException("set attribute fail")
        }
    }

    // get node attribute
    fun getAttribute(projectId: String, repoName: String, fullPath: String, key: String): String? {
        val result = metadataService.query(projectId, repoName, fullPath).data!!
        logger.debug("get attribute params : [$projectId,$repoName,$fullPath,$key] ,result: [$result]")
        return result[key]
    }

    // check node
    fun exists(projectId: String, repoName: String, fullPath: String): Boolean {
        return nodeResource.exist(projectId, repoName, fullPath).data ?: run {
            return false
        }
    }

    // check path read permission
    fun canRead(context: RequestContext): Boolean {
        with(context) {
            try {
                permissionService.checkPermission(
                    userId,
                    ResourceType.PROJECT,
                    PermissionAction.WRITE,
                    projectId,
                    repoName
                )
            } catch (e: PermissionCheckException) {
                logger.debug("user: [$userId] ,check read permission fail [$context]")
                return false
            }
            return true
        }
    }

    // check user write permission
    fun canWrite(context: RequestContext): Boolean {
        with(context) {
            try {
                permissionService.checkPermission(
                    userId,
                    ResourceType.PROJECT,
                    PermissionAction.WRITE,
                    projectId,
                    repoName
                )
            } catch (e: PermissionCheckException) {
                logger.debug("user: [$userId] ,check write permission fail [$context]")
                return false
            }
            return true
        }
    }

    // get artifact detail
    fun getArtifact(projectId: String, repoName: String, fullPath: String): DockerArtifact? {
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: run {
            logger.warn("get artifact detail failed: [$projectId, $repoName, $fullPath] found no artifact")
            return null
        }
        if (node.nodeInfo.sha256 == null) {
            logger.error("get artifact detail failed: [$projectId, $repoName, $fullPath] found no artifact")
            return null
        }
        return DockerArtifact(projectId, repoName, fullPath).sha256(node.nodeInfo.sha256!!)
            .length(node.nodeInfo.size)
    }

    // get artifact list by name
    fun getArtifactListByName(projectId: String, repoName: String, fileName: String): List<Map<String, Any>> {
        val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
        val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
        val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, fileName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT_SMALL),
            sort = Sort(listOf(DOCKER_NODE_FULL_PATH), Sort.Direction.ASC),
            select = mutableListOf(DOCKER_NODE_FULL_PATH, DOCKER_NODE_PATH, DOCKER_NODE_SIZE),
            rule = rule
        )

        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find artifact list failed: [$projectId, $repoName, $fileName] found no node")
            return emptyList()
        }
        return result.records
    }

    // get docker image list
    fun getDockerArtifactList(projectId: String, repoName: String): List<String> {
        val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
        val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
        val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, DOCKER_MANIFEST)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT),
            sort = Sort(listOf(DOCKER_NODE_FULL_PATH), Sort.Direction.ASC),
            select = mutableListOf(DOCKER_NODE_FULL_PATH, DOCKER_NODE_PATH, DOCKER_NODE_SIZE),
            rule = rule
        )

        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find repo list failed: [$projectId, $repoName] ")
            return emptyList()
        }
        val data = mutableListOf<String>()
        result.records.forEach {
            val path = it[DOCKER_NODE_PATH] as String
            data.add(
                path.removeSuffix(DOCKER_PRE_SUFFIX).replaceAfterLast(DOCKER_PRE_SUFFIX, EMPTYSTR).removeSuffix(
                    DOCKER_PRE_SUFFIX
                ).removePrefix(DOCKER_PRE_SUFFIX)
            )
        }
        return data.distinct()
    }

    // get repo tag list
    fun getRepoTagList(context: RequestContext): Map<String, String> {
        with(context) {
            val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
            val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
            val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, DOCKER_MANIFEST)
            val pathRule = Rule.QueryRule(DOCKER_NODE_PATH, "/$artifactName/", OperationType.PREFIX)
            val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule, pathRule))
            val queryModel = QueryModel(
                page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT),
                sort = Sort(listOf(DOCKER_NODE_FULL_PATH), Sort.Direction.ASC),
                select = mutableListOf(DOCKER_NODE_FULL_PATH, DOCKER_NODE_PATH, DOCKER_NODE_SIZE, DOCKER_CREATE_BY),
                rule = rule
            )

            val result = nodeResource.query(queryModel).data ?: run {
                logger.warn("find artifacts failed: [$projectId, $repoName] found no node")
                return emptyMap()
            }
            val data = mutableMapOf<String, String>()
            result.records.forEach {
                var path = it[DOCKER_NODE_PATH] as String
                val tag = path.removePrefix("/$artifactName/").removeSuffix(DOCKER_PRE_SUFFIX)
                val user = it[DOCKER_CREATE_BY] as String
                data[tag] = user
            }
            return data
        }
    }

    // get blob list by digest
    fun getBlobListByDigest(projectId: String, repoName: String, digestName: String): List<Map<String, Any>>? {
        val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
        val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
        val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, digestName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT),
            sort = Sort(listOf(DOCKER_NODE_PATH), Sort.Direction.ASC),
            select = mutableListOf(DOCKER_NODE_PATH, DOCKER_NODE_SIZE),
            rule = rule
        )
        val result = nodeResource.query(queryModel).data ?: run {
            logger.warn("find artifacts failed:  $digestName found no node")
            return null
        }
        return result.records
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactRepo::class.java)
    }
}
