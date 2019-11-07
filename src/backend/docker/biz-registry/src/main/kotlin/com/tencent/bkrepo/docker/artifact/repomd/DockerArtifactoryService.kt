package com.tencent.bkrepo.docker.artifact.repomd

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.common.storage.util.DataDigestUtils
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.DownloadContext
import com.tencent.bkrepo.docker.repomd.UploadContext
import com.tencent.bkrepo.docker.repomd.WriteContext
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import java.io.File
import java.io.InputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DockerArtifactoryService @Autowired constructor(
        private val repositoryResource: RepositoryResource,
        private val nodeResource: NodeResource,
        private val fileStorage: FileStorage,
        private val metadataService: MetadataResource

) {

    // protected var propertiesService: PropertiesService ？
    protected lateinit var repoKey: String
    protected lateinit var context: DockerWorkContext
    private val localPath: String = "/Users/owen/data"

    init {
        this.context = DockerPackageWorkContext()
        this.repoKey = "docker-local"
    }

    fun writeLocal(path: String, name: String, inputStream: InputStream): ResponseEntity<Any> {

        val filePath = localPath + path
        var fullPath = localPath + path + "/" + name

        File(filePath).mkdirs()
        val file = File(fullPath)
        if (!file.exists()) {
            file.createNewFile()
            file.writeBytes(inputStream.readBytes())
        } else {
            file.appendBytes(inputStream.readBytes())
        }
        return ResponseEntity.ok().body("ok")
    }

    fun readLocal(projectId: String,repoName: String,path: String):InputStream{
        var fullPath = "$localPath/$projectId/$repoName/$path"
        return  File(fullPath).inputStream()
    }
//    override  fun read(path: String): InputStream {
//        val repoPath = this.repoPath(path)
//        log.debug("Acquiring the content stream for '{}'", repoPath)
//
//        try {
//            val requestContext = NullRequestContext(repoPath)
//            val repo = this.repoService.getLocalRepository(repoPath)
//            val info = repo.getInfo(requestContext)
//            val handle = this.repoService.getResourceStreamHandle(requestContext, repo, info)
//            return handle.getInputStream()
//        } catch (var7: BinaryRejectedException) {
//            throw IllegalStateException("Unauthorized: " + var7.getMessage(), var7)
//        } catch (var7: RepoRejectException) {
//            throw IllegalStateException("Unauthorized: " + var7.getMessage(), var7)
//        } catch (var8: Exception) {
//            throw IllegalStateException("Failed to retrieve resource " + repoPath + ": " + var8.message, var8)
//        }
//    }


    fun readGlobal(context: DownloadContext): InputStream {
        // query repository
        val repository = repositoryResource.queryDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$context.userId] simply download file  [$context.path] failed: $context.repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, context.repoName)
        }

        // get content from storage
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        var file = fileStorage.load(context.sha256, storageCredentials)
        return file!!.inputStream()
    }

    fun getRepoId(): String {
        return this.repoKey
    }

    fun getWorkContextC(): DockerWorkContext {
        return this.context
    }

    fun write(path: String, stream: InputStream) {
    }

    fun write(context: WriteContext) {
        try {
            // check the repo
            val repository = repositoryResource.queryDetail(context.projectId , context.repoName, REPO_TYPE).data
                    ?: run {
                        logger.warn("user[$context.userId]  upload file  [$context.path] failed: ${context.repoName} not found")
                        throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, context.repoName)
                    }

            // save the node
            val result = nodeResource.create(NodeCreateRequest(
                    projectId = context.projectId,
                    repoName = context.repoName,
                    folder = false,
                    fullPath = context.path,
                    size = context.contentLength,
                    sha256 = context.sha256,
                    operator = context.userId
            ))

            if (result.isOk()) {
                val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
                fileStorage.store(context.sha256, context.content!!, storageCredentials)
                logger.info("user[$context.userId] simply upload file [$context.path] success")
            } else {
                logger.warn("user[$context.userId] simply upload file [$context.path] failed: [${result.code}, ${result.message}]")
                throw ExternalErrorCodeException(result.code, result.message)
            }
        } catch (exception: Exception) {
            throw RuntimeException("Failed to save stream to ${context.path}", exception)
        }
    }

    fun delete(path: String): Boolean {
        return true
    }

    fun deleteLocal(path: String): Boolean {
        var f = File(localPath + path)
        return f.delete()
    }

    fun download(context: DownloadContext): File {
        // query repository
        val repository = repositoryResource.queryDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$context.userId] simply download file  [$context.path] failed: $context.repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, context.repoName)
        }

        // fileStorage
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        var file = fileStorage.load(context.sha256, storageCredentials) // ?: run {
//             logger.warn("user[$context.userId] simply download file [$context.path] failed: file data not found")
//         }
        // File("aaaa")
        // return ResponseEntity.ok().body("ok")
        return file!!
    }

//    private fun getInternalArtifactoryRequestForDownload(downloadContext: DownloadContext): InternalArtifactoryRequest {
//        val requestHeaders = downloadContext.getRequestHeaders()
//        val isRedirectDisabled = requestHeaders.get("artifactory.disableRedirect") as String
//        checkNotNull(isRedirectDisabled) { "Must provide artifactory.disableRedirect header as part of request" }
//        val repoPath = this.repoPath(downloadContext.getPath())
//        val req = if (java.lang.Boolean.valueOf(isRedirectDisabled)) InternalRequestFactory.createInternalRequestDisableRedirect(repoPath) else InternalRequestFactory.createInternalRequestEnableRedirect(repoPath)
//        req.setSkipStatsUpdate(downloadContext.isSkipStatsUpdate())
//        req.addHeaders(requestHeaders)
//        return req
//    }

    @Transactional(rollbackFor = [Throwable::class])
    fun upload(context: UploadContext): ResponseEntity<Any> {
        // TODO: 校验权限

        // 校验sha256
//        val calculatedSha256 = FileDigestUtils.fileSha256(listOf(context.content!!))
//        if (context.sha256 != null && calculatedSha256 != context.sha256) {
//            logger.warn("user[${context.userId}]  upload  file [$fullUri] failed: file sha256 verification failed")
//            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
//        }

        // 判断仓库是否存在
        val repository = repositoryResource.queryDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$context.userId]  upload file  [$context.path] failed: ${context.repoName} not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, context.repoName)
        }

        // 保存节点
        val result = nodeResource.create(NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.path,
                size = context.contentLength,
                sha256 = context.sha256,
                operator = context.userId
        ))

        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            fileStorage.store(context.sha256, context.content!!, storageCredentials)
            logger.info("user[$context.userId] simply upload file [$context.path] success")
        } else {
            logger.warn("user[$context.userId] simply upload file [$context.path] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
        return ResponseEntity.ok().body("ok")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun uploadFromLocal(path: String, context: UploadContext): ResponseEntity<Any> {
        // TODO: 校验权限

        // 校验sha256
//        val calculatedSha256 = FileDigestUtils.fileSha256(listOf(context.content!!))
//        if (context.sha256 != null && calculatedSha256 != context.sha256) {
//            logger.warn("user[${context.userId}]  upload  file [$fullUri] failed: file sha256 verification failed")
//            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
//        }

        // 判断仓库是否存在
        val repository = repositoryResource.queryDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$context.userId]  upload file  [$context.path] failed: ${context.repoName} not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, context.repoName)
        }
        var fullPath = localPath + path
        var content = File(fullPath).readBytes()
        context.content(content.inputStream()).contentLength(content.size.toLong()).sha256(DataDigestUtils.sha256FromByteArray(content))

        // 保存节点
        val result = nodeResource.create(NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.path,
                size = context.contentLength,
                sha256 = context.sha256,
                operator = context.userId
        ))
        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            fileStorage.store(context.sha256, context.content!!, storageCredentials)
            logger.info("user[$context.userId] simply upload file [$context.path] success")
        } else {
            logger.warn("user[$context.userId] simply upload file [$context.path] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
        return ResponseEntity.ok().body("ok")
    }

//    private fun contentLength(context: UploadContext): Long {
//        if (context.getContentLength() > 0L) {
//            return context.getContentLength()
//        } else {
//            val headers = context.getRequestHeaders()
//            if (headers != null) {
//                val headerValue = (headers!!.entries.stream().filter({ entry -> "Content-Length".equals(entry.key as String, ignoreCase = true).toLong() }).findFirst().map({ entry -> Optional.ofNullable<String>(entry.value).toLong() }).orElse(Optional.empty<String>()) as Optional<*>).orElse("-1") as String
//
//                try {
//                    return java.lang.Long.valueOf(headerValue)
//                } catch (var5: NumberFormatException) {
//                    log.warn("Content-Length header value is not a number: '{}' (path: )", headerValue, context.getPath())
//                }
//
//            }
//
//            return -1L
//        }
//    }

    fun copy(from: String, to: String): Boolean {
        return true
//        val status = this.repoService.copyMultiTx(this.repoPath(from), this.repoPath(to), false, true, true)
//        return !status.isError() && !status.hasWarnings()
    }

    fun move(from: String, to: String): Boolean {
        return true
//        val status = this.repoService.moveMultiTx(this.repoPath(from), this.repoPath(to), false, true, true)
//        return !status.isError() && !status.hasWarnings()
    }

//    override fun getAttribute(path: String, key: String): Any? {
//        val properties = this.propertiesService.getProperties(this.repoPath(path))
//        return if (properties.containsKey(key)) properties.getFirst(key) else null
//    }

//    open fun getAttributes(path: String, key: String): Set<*>? {
//        val properties = this.propertiesService.getProperties(this.repoPath(path))
//        return if (properties.containsKey(key)) properties.get(key) else null
//    }

    fun setAttribute(path: String, key: String, value: Any) {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
        // this.setProperties(path, key, value)
    }

    fun setAttributes(path: String, key: String, vararg values: Any) {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
        //   this.setProperties(path, key, *values)
    }

//    fun addAttribute(path: String, key: String, vararg values: Any) {
//        this.addProperty(path, key, values)
//    }

//    fun removeAttribute(path: String, key: String, vararg values: Any) {
//        this.removePropertyValues(path, key, values)
//    }

    fun setAttributes(path: String, keyValueMap: Map<String, String>) {

        throw UnsupportedOperationException("NOT IMPLEMENTED")
        // this.setProperties(path, keyValueMap)
    }

    fun setAttributes(projectId: String, repoName: String, path: String, keyValueMap: Map<String, String>) {
        metadataService.upsert(MetadataUpsertRequest(projectId, repoName, path, keyValueMap, "bk_admin"))
    }

    fun exists(path: String): Boolean {
        return true
//        return this.repoService.exists(this.repoPath(path))
    }

    fun exists(projectId: String, repoName: String, dockerRepo: String): Boolean {
        return nodeResource.exist(projectId, repoName, dockerRepo).data!!
//        return this.repoService.exists(this.repoPath(path))
    }

    fun existsLocal(projectId:String, repoName:String, path: String): Boolean {
        val fullPath = "$localPath/$projectId/$repoName/$path"
        val file = File(fullPath)
        return file.exists()
    }

    fun canRead(path: String): Boolean {
        return true
//        return this.authorizationService.canRead(this.repoPath(path))
    }

    fun canWrite(path: String): Boolean {
        return true
//        val repoPath = this.repoPath(path)
//        val repo = this.repoService.getLocalRepository(repoPath)
//        val statusHolder = repo.assertValidPath(repoPath, false)
//        if (statusHolder.isError()) {
//            log.warn(statusHolder.getStatusMsg())
//            return false
//        } else {
//            return this.authorizationService.canDeploy(this.repoPath(path))
//        }
    }

    fun canDelete(path: String): Boolean {
        return true
//        return this.authorizationService.canDelete(this.repoPath(path))
    }

    private fun setProperties(path: String, key: String, vararg values: Any) {
    }

    private fun setProperties(path: String, propsMap: Map<String, String>) {
    }

    private fun addProperty(path: String, propKey: String, values: Array<Any>) {
    }

    private fun removePropertyValues(path: String, propKey: String, values: Array<Any>) {
    }

    fun artifactLocal(projectId: String, repoName: String, dockerRepo: String): Artifact? {
        val fullPath = "$localPath/$projectId/$repoName/$dockerRepo"
        val file = File(fullPath)
        val content = file.readBytes()
        val sha256 = DataDigestUtils.sha256FromByteArray(content)
        var length = content.size.toLong()
        return Artifact(projectId, repoName, dockerRepo).sha256(sha256).contentLength(length)
    }

    fun artifact(projectId: String, repoName: String, dockerRepo: String): Artifact? {
        val fullPath = localPath + ""
        val file = File(fullPath)
        val content = file.readBytes()
        val sha256 = DataDigestUtils.sha256FromByteArray(content)
        var length = content.size.toLong()
        return Artifact(projectId, repoName, fullPath).sha256(sha256).contentLength(length)
    }

    fun findArtifacts(projectId: String, repoName: String, name: String): NodeDetail? {
        // 查询node节点
        val nodes = nodeResource.queryDetail(projectId, repoName, name).data ?: run {
            logger.warn("find artifacts  failed: $projectId, $repoName, $name found no artifacts")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, projectId + ":" + repoName + ":" + name)
        }
        return nodes
    }

    fun findArtifacts(fileName: String): Iterable<Artifact> {
//        var nodeSearchParams = NodeSearchRequest()
//        nodeResource.search()
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }


    fun findArtifacts(var1: String, var2: String): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    fun getAttribute(path: String, key: String): Any {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }


    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactoryService::class.java)
    }
}
