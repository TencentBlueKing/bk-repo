package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.constant.HTTP_FORWARDED_PROTO
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTP
import com.tencent.bkrepo.docker.constant.HTTP_PROTOCOL_HTTPS
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.errors.DockerV2Errors
import com.tencent.bkrepo.docker.manifest.ManifestType
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.Objects
import java.util.regex.Pattern
import javax.ws.rs.core.UriBuilder
import kotlin.streams.toList

class RepoServiceUtil {

    companion object {

        private val logger = LoggerFactory.getLogger(RepoServiceUtil::class.java)
        private val OLD_USER_AGENT_PATTERN = Pattern.compile("^(?:docker\\/1\\.(?:3|4|5|6|7(?!\\.[0-9]-dev))|Go ).*$")

        fun putHasStream(httpHeaders: HttpHeaders): Boolean {
            val headerValues = httpHeaders["User-Agent"]
            if (headerValues != null) {
                val headerIter = headerValues.iterator()
                while (headerIter.hasNext()) {
                    val userAgent = headerIter.next() as String
                    logger.info("User agent header: [$userAgent]")
                    if (OLD_USER_AGENT_PATTERN.matcher(userAgent).matches()) {
                        return true
                    }
                }
            }
            return false
        }

        fun consumeStreamAndReturnError(stream: InputStream): ResponseEntity<Any> {
            NullOutputStream().use {
                IOUtils.copy(stream, it)
            }
            return DockerV2Errors.unauthorizedUpload()
        }

        fun getProtocol(httpHeaders: HttpHeaders): String {
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

        fun getAcceptableManifestTypes(httpHeaders: HttpHeaders): List<ManifestType> {
            return httpHeaders.accept.stream().filter { Objects.nonNull(it) }.map { ManifestType.from(it) }.toList()
        }

        fun manifestListUploadContext(
            projectId: String,
            repoName: String,
            manifestType: ManifestType,
            digest: DockerDigest,
            manifestPath: String,
            manifestBytes: ByteArray
        ): UploadContext {
            val context = UploadContext(projectId, repoName, manifestPath).content(ByteArrayInputStream(manifestBytes))
            if (manifestType == ManifestType.Schema2List && "sha256" == digest.getDigestAlg()) {
                context.sha256(digest.getDigestHex())
            }
            return context
        }

        fun buildManifestPropertyMap(
            dockerRepo: String,
            tag: String,
            digest: DockerDigest,
            manifestType: ManifestType
        ): HashMap<String, String> {
            var map = HashMap<String, String>()
            map[digest.getDigestAlg()] = digest.getDigestHex()
            map["docker.manifest.digest"] = digest.toString()
            map["docker.manifest"] = tag
            map["docker.repoName"] = dockerRepo
            map["docker.manifest.type"] = manifestType.toString()
            return map
        }

        fun manifestUploadContext(
            projectId: String,
            repoName: String,
            manifestType: ManifestType,
            manifestMetadata: ManifestMetadata,
            manifestPath: String,
            manifestBytes: ByteArray,
            artifactFile: ArtifactFile
        ): UploadContext {
            val context = UploadContext(projectId, repoName, manifestPath).content(ByteArrayInputStream(manifestBytes))
                .artifactFile(artifactFile)
            if ((manifestType == ManifestType.Schema2 || manifestType == ManifestType.Schema2List) && "sha256" == manifestMetadata.tagInfo.digest?.getDigestAlg()) {
                context.sha256(manifestMetadata.tagInfo.digest!!.getDigestHex())
            }

            return context
        }

        fun getDockerURI(path: String, httpHeaders: HttpHeaders): URI {
            val hostHeaders = httpHeaders["Host"]
            var host = ""
            var port: Int? = null
            if (hostHeaders != null && hostHeaders.isNotEmpty()) {
                val parts =
                    (hostHeaders[0] as String).split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                host = parts[0]
                if (parts.size > 1) {
                    port = Integer.valueOf(parts[1])
                }
            } else {
                logger.error("docker location url is blank, make sure the host request header exists.")
            }

            val builder = UriBuilder.fromPath("v2/$path").host(host).scheme(RepoServiceUtil.getProtocol(httpHeaders))
            if (port != null) {
                builder.port(port)
            }

            return builder.build(*arrayOfNulls(0))
        }

        fun buildManifestPathFromType(dockerRepo: String, tag: String, manifestType: ManifestType): String {
            return if (ManifestType.Schema2List == manifestType) {
                "/$dockerRepo/$tag/list.manifest.json"
            } else {
                "/$dockerRepo/$tag/manifest.json"
            }
        }
    }
}
