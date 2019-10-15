package com.tencent.bkrepo.docker.artifact.repomd

// import com.tencent.bkrepo.registry.common.repomd.ArtifactoryService
import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.repomd.Artifact
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.repomd.UploadContext
import java.io.InputStream
import javax.ws.rs.core.Response

class DockerArtifactoryService(var workContext: DockerWorkContext, var id: String) : Repo<DockerWorkContext> {

    // protected var propertiesService: PropertiesService ？

    protected lateinit var context: DockerWorkContext
    protected lateinit var repoKey: String

    init {
        this.context = workContext
        this.repoKey = id
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

    override fun getRepoId(): String {
        return this.repoKey
    }

    override fun getWorkContextC(): DockerWorkContext {
        return this.context
    }

    override fun write(path: String, `in`: InputStream) {
//        val repoPath = this.repoPath(path)
//
//        try {
//            this.repoService.saveFileInternal(repoPath, `in`)
//        } catch (var5: Exception) {
//            throw RuntimeException("Failed to save stream to $repoPath", var5)
//        }
    }

    override fun delete(path: String): Boolean {
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

//    fun download(downloadContext: DownloadContext): Response? {
//        val req = this.getInternalArtifactoryRequestForDownload(downloadContext)
//        val res = JerseyArtifactoryResponse()
//
//        try {
//            this.downloadService.process(req, res)
//            return res.build()
//        } catch (var5: IOException) {
//            log.error(var5.message, var5)
//            return null
//        }
//
//    }

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

    override fun upload(context: UploadContext): Response {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
//        val res = JerseyArtifactoryResponse()
//        val properties = this.parseUploadProperties(context)
//        val req = ArtifactoryDeployRequestBuilder(this.repoPath(context.getPath())).inputStream(context.getContent()).properties(properties).contentLength(this.contentLength(context)).build()
//        val headers = Maps.newHashMap<String, String>()
//        headers.putAll(context.getRequestHeaders())
//        if (StringUtils.isNotBlank(context.getSha1())) {
//            headers["X-Checksum-Sha1"] = context.getSha1()
//        }
//
//        if (StringUtils.isNotBlank(context.getSha256())) {
//            headers["X-Checksum-Sha256"] = context.getSha256()
//        }
//
//        if (StringUtils.isNotBlank(context.getMd5())) {
//            headers["X-Checksum-Md5"] = context.getMd5()
//        }
//
//        req.addHeaders(headers)
//
//        try {
//            this.uploadService.upload(req, res)
//            return res.build()
//        } catch (var7: RestException) {
//            return Response.status(var7.getStatusCode()).entity(var7.getMessage()).build()
//        } catch (var8: RepoRejectException) {
//            log.error(var8.getMessage(), var8)
//            return Response.status(Response.Status.BAD_REQUEST).entity(var8.getMessage()).build()
//        } catch (var8: IOException) {
//            log.error(var8.getMessage(), var8)
//            return Response.status(Response.Status.BAD_REQUEST).entity(var8.getMessage()).build()
//        }
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

    override fun copy(from: String, to: String): Boolean {
        return true
//        val status = this.repoService.copyMultiTx(this.repoPath(from), this.repoPath(to), false, true, true)
//        return !status.isError() && !status.hasWarnings()
    }

    override fun move(from: String, to: String): Boolean {
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

    override fun setAttribute(path: String, key: String, value: Any) {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
        // this.setProperties(path, key, value)
    }

    override fun setAttributes(path: String, key: String, vararg values: Any) {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
        //   this.setProperties(path, key, *values)
    }

//    fun addAttribute(path: String, key: String, vararg values: Any) {
//        this.addProperty(path, key, values)
//    }

//    fun removeAttribute(path: String, key: String, vararg values: Any) {
//        this.removePropertyValues(path, key, values)
//    }

    override fun setAttributes(path: String, keyValueMap: Map<String, String>) {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
        // this.setProperties(path, keyValueMap)
    }

    override fun exists(path: String): Boolean {
        return true
//        return this.repoService.exists(this.repoPath(path))
    }

    override fun canRead(path: String): Boolean {
        return true
//        return this.authorizationService.canRead(this.repoPath(path))
    }

    override fun canWrite(path: String): Boolean {
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

    override fun artifact(path: String): Artifact? {
        // TODO("consctruct Artifact from path")
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    override fun findArtifacts(query: String): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    override fun findArtifacts(var1: String, var2: String): Iterable<Artifact> {
        throw UnsupportedOperationException("NOT IMPLEMENTED")
    }

    override fun getAttribute(path: String, key: String): Any {
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
}
