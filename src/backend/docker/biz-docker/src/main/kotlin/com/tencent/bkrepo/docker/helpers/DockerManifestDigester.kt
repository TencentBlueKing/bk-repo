package com.tencent.bkrepo.docker.helpers

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Charsets
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.docker.constant.DOCKER_FORMAT_LENGTH
import com.tencent.bkrepo.docker.constant.DOCKER_FORMAT_TAIL
import com.tencent.bkrepo.docker.constant.DOCKER_PROTECT
import com.tencent.bkrepo.docker.constant.DOCKER_SCHEMA_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_SIG
import com.tencent.bkrepo.docker.model.DockerDigest
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory

/**
 * to parse manifest digest
 * @author: owenlxu
 * @date: 2020-01-05
 */
object DockerManifestDigester {

    private val logger = LoggerFactory.getLogger(DockerManifestDigester::class.java)

    /**
     * calculate digest from config byte data
     * @param configBytes docker manifest config byte
     * @return DockerDigest metadata property
     */
    fun calc(configBytes: ByteArray): DockerDigest? {
        val manifest = mapper().readTree(configBytes)
        val schemaVersion = manifest.get(DOCKER_SCHEMA_VERSION) ?: run {
            logger.warn("unable to determine the schema version of the manifest")
            return null
        }
        val schema = schemaVersion.asInt()
        val digest = if (schema == 1) {
            schema1Digest(configBytes, manifest)
        } else {
            if (schema != 2) {
                logger.warn("unknown schema version [$schema] for manifest file")
                return null
            }
            schema2Digest(configBytes)
        }
        return DockerDigest.fromSha256(digest)
    }

    private fun schema2Digest(jsonBytes: ByteArray): String {
        val digest = DigestUtils.getSha256Digest()
        DigestUtils.updateDigest(digest, jsonBytes)
        return Hex.encodeHexString(digest.digest())
    }

    private fun schema1Digest(jsonBytes: ByteArray, manifest: JsonNode): String {
        var formatLength = 0
        var formatTail = EMPTY
        val signatures = manifest.get(DOCKER_SIG) ?: run {
            return getHexDigest(jsonBytes, formatLength, formatTail)
        }
        val sig = signatures.iterator()
        while (sig.hasNext()) {
            val signature = sig.next() as JsonNode
            var protectedJson = signature.get(DOCKER_PROTECT)
            protectedJson?.let {
                val protectedBytes = Base64.decodeBase64(protectedJson.asText())
                protectedJson = mapper().readTree(protectedBytes)
                formatLength = protectedJson.get(DOCKER_FORMAT_LENGTH).asInt()
                formatTail = protectedJson.get(DOCKER_FORMAT_TAIL).asText()
                formatTail = String(Base64.decodeBase64(formatTail), Charsets.UTF_8)
            }
        }

        return getHexDigest(jsonBytes, formatLength, formatTail)
    }

    private fun getHexDigest(jsonBytes: ByteArray, formatLength: Int, formatTail: String): String {
        val formatTailLength = formatTail.length
        val bytes = jsonBytes.copyOf(formatLength + formatTailLength)
        System.arraycopy(formatTail.toByteArray(), 0, bytes, formatLength, formatTailLength)
        val digest = DigestUtils.getSha256Digest()
        DigestUtils.updateDigest(digest, bytes)
        return Hex.encodeHexString(digest.digest())
    }

    private fun mapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return mapper
    }
}
