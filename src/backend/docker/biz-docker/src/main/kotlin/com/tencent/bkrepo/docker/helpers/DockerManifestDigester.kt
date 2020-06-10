package com.tencent.bkrepo.docker.helpers

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Charsets
import com.tencent.bkrepo.docker.model.DockerDigest
import java.io.IOException
import java.util.Arrays
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory

object DockerManifestDigester {
    private val logger = LoggerFactory.getLogger(DockerManifestDigester::class.java)

    @Throws(IOException::class)
    fun calc(jsonBytes: ByteArray): DockerDigest? {
        val manifest = mapper().readTree(jsonBytes)
        val schemaVersion = manifest.get("schemaVersion")
        if (schemaVersion == null) {
            logger.error("unable to determine the schema version of the manifest")
            return null
        } else {
            val schema = schemaVersion.asInt()
            val digest: String
            if (schema == 1) {
                digest = schema1Digest(jsonBytes, manifest)
            } else {
                if (schema != 2) {
                    logger.warn("unknown schema version '{}' for manifest file", schema)
                    return null
                }

                digest = schema2Digest(jsonBytes)
            }

            return DockerDigest("sha256:$digest")
        }
    }

    private fun schema2Digest(jsonBytes: ByteArray): String {
        val digest = DigestUtils.getSha256Digest()
        DigestUtils.updateDigest(digest, jsonBytes)
        return Hex.encodeHexString(digest.digest())
    }

    @Throws(IOException::class)
    private fun schema1Digest(jsonBytes: ByteArray, manifest: JsonNode): String {
        var formatLength = 0
        var formatTail = ""
        val signatures = manifest.get("signatures")
        if (signatures != null) {
            val var5 = signatures.iterator()

            while (var5.hasNext()) {
                val signature = var5.next() as JsonNode
                var protectedJson = signature.get("protected")
                if (protectedJson != null) {
                    val protectedBytes = Base64.decodeBase64(protectedJson.asText())
                    protectedJson = mapper().readTree(protectedBytes)
                    formatLength = protectedJson.get("formatLength").asInt()
                    formatTail = protectedJson.get("formatTail").asText()
                    formatTail = String(Base64.decodeBase64(formatTail), Charsets.UTF_8)
                }
            }
        }

        return getHexDigest(jsonBytes, formatLength, formatTail)
    }

    private fun getHexDigest(jsonBytes: ByteArray, formatLength: Int, formatTail: String): String {
        val formatTailLength = formatTail.length
        val bytes = Arrays.copyOf(jsonBytes, formatLength + formatTailLength)
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
