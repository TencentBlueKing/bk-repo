package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_LIST
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_TYPE
import com.tencent.bkrepo.docker.constant.DOCKER_NAME_REPO
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.constant.HTTP_FORWARDED_PROTO
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTP
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTPS
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.response.DockerResponse
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import java.io.InputStream
import java.net.URI
import java.util.Objects
import java.util.regex.Pattern
import javax.ws.rs.core.UriBuilder
import kotlin.streams.toList

/**
 * docker repo service utility
 * @author: owenlxu
 * @date: 2019-11-15
 */
class RepoServiceUtil {

    companion object {

        private val logger = LoggerFactory.getLogger(RepoServiceUtil::class.java)
        private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")

        fun putHasStream(httpHeaders: HttpHeaders): Boolean {
            val headerValues = httpHeaders["User-Agent"]
            headerValues?.let {
                val headerIter = it.iterator()
                while (headerIter.hasNext()) {
                    val userAgent = headerIter.next() as String
                    logger.debug("User agent header: [$userAgent]")
                    if (OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()) {
                        return true
                    }
                }
            }
            return false
        }

        fun consumeStreamAndReturnError(stream: InputStream): DockerResponse {
            NullOutputStream().use {
                IOUtils.copy(stream, it)
            }
            return DockerV2Errors.unauthorizedUpload()
        }

        fun getAcceptableManifestTypes(httpHeaders: HttpHeaders): List<ManifestType> {
            return httpHeaders.accept.stream().filter { Objects.nonNull(it) }.map { ManifestType.from(it) }.toList()
        }

        fun manifestListUploadContext(context: RequestContext, digest: DockerDigest, path: String, bytes: ByteArray): UploadContext {
            with(context) {
                val artifactFile = ArtifactFileFactory.build(bytes.inputStream())
                val uploadContext = UploadContext(projectId, repoName, path).artifactFile(artifactFile)
                uploadContext.sha256(digest.getDigestHex())
                return uploadContext
            }
        }

        fun buildManifestPropertyMap(dockerRepo: String, tag: String, digest: DockerDigest, type: ManifestType): HashMap<String, String> {
            var map = HashMap<String, String>()
            map[digest.getDigestAlg()] = digest.getDigestHex()
            map[DOCKER_MANIFEST_DIGEST] = digest.toString()
            map[DOCKER_MANIFEST_NAME] = tag
            map[DOCKER_NAME_REPO] = dockerRepo
            map[DOCKER_MANIFEST_TYPE] = type.toString()
            return map
        }

        fun manifestUploadContext(context: RequestContext, type: ManifestType, metadata: ManifestMetadata, path: String, file: ArtifactFile): UploadContext {
            with(context) {
                val uploadContext = UploadContext(projectId, repoName, path).artifactFile(file)
                if ((type == ManifestType.Schema2 || type == ManifestType.Schema2List) && "sha256" == metadata.tagInfo.digest?.getDigestAlg()) {
                    uploadContext.sha256(metadata.tagInfo.digest!!.getDigestHex())
                }
                return uploadContext
            }
        }

        //get return url
        fun getDockerURI(path: String, httpHeaders: HttpHeaders): URI {
            val hostHeaders = httpHeaders["Host"]
            var host = EMPTYSTR
            var port: Int? = null
            if (hostHeaders != null && hostHeaders.isNotEmpty()) {
                val parts = (hostHeaders[0] as String).split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                host = parts[0]
                if (parts.size > 1) {
                    port = Integer.valueOf(parts[1])
                }
            }

            val builder = UriBuilder.fromPath("v2/$path").host(host).scheme(RepoServiceUtil.getProtocol(httpHeaders))
            port?.let {
                builder.port(port)
            }
            return builder.build()
        }

        //build return manifest path
        fun buildManifestPath(dockerRepo: String, tag: String, manifestType: ManifestType): String {
            return if (ManifestType.Schema2List == manifestType) {
                "/$dockerRepo/$tag/$DOCKER_MANIFEST_LIST"
            } else {
                "/$dockerRepo/$tag/$DOCKER_MANIFEST"
            }
        }

        //get http protocol from request head
        private fun getProtocol(httpHeaders: HttpHeaders): String {
            val protocolHeaders = httpHeaders[HTTP_FORWARDED_PROTO]
            if (protocolHeaders == null || protocolHeaders.isEmpty()) {
                return HTTP_PROTOCOL_HTTP
            }
            return if (protocolHeaders.isNotEmpty()) {
                protocolHeaders.iterator().next() as String
            } else {
                logger.debug("X-Forwarded-Proto does not exist, return https.")
                HTTP_PROTOCOL_HTTPS
            }
        }
    }
}
