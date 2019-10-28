package com.tencent.bkrepo.docker.artifact.repomd

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.DownloadContext
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.repomd.UploadContext
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.springframework.beans.factory.annotation.Autowired
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import java.io.InputStream
import javax.ws.rs.core.Response
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.tencent.bkrepo.docker.artifact.repomd.DockerPackageWorkContext
import org.apache.commons.io.IOUtils
import com.tencent.bkrepo.common.storage.util.DataDigestUtils
import java.io.File
import java.nio.file.Path


@Service
class DockerArtifactoryService @Autowired constructor(
        private val repositoryResource: RepositoryResource,
        private val nodeResource: NodeResource,
        private val fileStorage: FileStorage

)  {

    // protected var propertiesService: PropertiesService ？
    protected lateinit var repoKey: String
    protected lateinit var context: DockerWorkContext
    private val localPath :String ="/Users/owen/data"



    init {
        this.context = DockerPackageWorkContext()
        this.repoKey = "docker-local"
    }



    fun writeLocal(path: String, name :String,  inputStream: InputStream) :ResponseEntity<Any> {

        val filePath = localPath + path
        var fullPath = localPath + path + "/"+name

        val f = File(filePath).mkdirs()
        val file  = File(fullPath)
        if (!file.exists()) {
            file.createNewFile()
            file.writeBytes(inputStream.readBytes())
        }else{
            file.appendBytes(inputStream.readBytes())
        }
        return ResponseEntity.ok().body("ok")
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

     fun getRepoId(): String {
        return this.repoKey
    }

     fun getWorkContextC(): DockerWorkContext {
        return this.context
    }

     fun write(path: String, `in`: InputStream) {
//        val repoPath = this.repoPath(path)
//
//        try {
//            this.repoService.saveFileInternal(repoPath, `in`)
//        } catch (var5: Exception) {
//            throw RuntimeException("Failed to save stream to $repoPath", var5)
//        }
    }

     fun delete(path: String): Boolean {
        print("wwwwwwwww")
        return true
//        val statusHolder: BasicStatusHolder
//        if (this.repoService.virtualRepositoryByKey(this.id) != null) {
//            statusHolder = this.repoService.undeploy(this.repoPath(path), false)
//        } else {
//            statusHolder = this.repoService.undeploy(this.repoPath(path), false, true)
//        }
//
//        return !statusHolder.isError()
    }

     fun download(downloadContext: DownloadContext): Response {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
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
                fullPath = context.path ,
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
    fun uploadFromLocal(path: String,context: UploadContext): ResponseEntity<Any> {
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
        var content  = File(fullPath).readBytes()
        context.content(content.inputStream()).contentLength(content.size.toLong())

        // 保存节点
        val result = nodeResource.create(NodeCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                folder = false,
                fullPath = context.path ,
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

     fun exists(path: String): Boolean {
        return true
//        return this.repoService.exists(this.repoPath(path))
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
//        val existingProperties = this.propertiesService.getProperties(this.repoPath(path))
//        existingProperties.removeAll(key)
//        val var6 = values.size
//
//        for (var7 in 0 until var6) {
//            val value = values[var7]
//            if (value != null) {
//                existingProperties.put(key, value.toString())
//            }
//        }
//
//        this.propertiesService.setProperties(this.repoPath(path), existingProperties, true)
    }

    private fun setProperties(path: String, propsMap: Map<String, String>) {
//        val finalExistingProperties = this.propertiesService.getProperties(this.repoPath(path))
//        propsMap.forEach { (key, value) ->
//            finalExistingProperties.removeAll(key)
//            if (value != null) {
//                finalExistingProperties.put(key, value)
//            }
//
//        }
//        this.propertiesService.setProperties(this.repoPath(path), finalExistingProperties, true)
    }

    private fun addProperty(path: String, propKey: String, values: Array<Any>) {
//        if (ArrayUtils.isNotEmpty(values)) {
//            val valuesToAdd = HashSet(Arrays.asList(*values))
//            this.propertiesService.addProperties(this.repoPath(path), propKey, valuesToAdd, true)
//        }
    }

    private fun removePropertyValues(path: String, propKey: String, values: Array<Any>) {
//        if (ArrayUtils.isNotEmpty(values)) {
//            val valuesToRemove = HashSet(Arrays.asList(*values))
//            this.propertiesService.removePropertyValues(this.repoPath(path), propKey, valuesToRemove, true)
//        }
    }

     fun artifact(path: String): Artifact? {
         val fullPath = localPath + path
         val file = File(fullPath)
         val content = file.readBytes()
         val sha256  = DataDigestUtils.sha256FromByteArray(content)
         var length = content.size
         return Artifact(length,sha256)
//         val contents = file.readText()
//         println(contents)
        // TODO("consctruct Artifact from path")
      //  throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

     fun findArtifacts(query: String): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

     fun findArtifacts(var1: String, var2: String): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

     fun getAttribute(path: String, key: String): Any {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

//    protected fun repoPath(path: String?): RepoPath {
//        return RepoPathFactory.create(this.id, path)
//    }
//
//    private fun parseUploadProperties(context: UploadContext): Properties {
//        val contextAttributes = context.getAttributes()
//        val properties = InfoFactoryHolder.get().createProperties() as Properties
//        contextAttributes.forEach(BiConsumer<String, String> { properties.put() })
//        return properties
//    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerArtifactoryService::class.java)
    }
}
