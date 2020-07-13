package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.docker.constant.DOCKER_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_SIZE
import com.tencent.bkrepo.docker.constant.EMPTYSTR
import com.tencent.bkrepo.docker.model.DockerBlobInfo
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.DockerImageMetadata
import com.tencent.bkrepo.docker.model.ManifestMetadata
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.stream.StreamSupport

/**
 * to deserialize manifest schema2 manifest
 * @author: owenlxu
 * @date: 2020-02-05
 */
class ManifestSchema2Deserializer {
    companion object {
        private val logger = LoggerFactory.getLogger(ManifestSchema2Deserializer::class.java)
        private const val CIRCUIT_BREAKER_THRESHOLD = 5000

        fun deserialize(manifestBytes: ByteArray, jsonBytes: ByteArray, dockerRepo: String, tag: String, digest: DockerDigest): ManifestMetadata {
            try {
                val manifestMetadata = ManifestMetadata()
                manifestMetadata.tagInfo.title = "$dockerRepo:$tag"
                manifestMetadata.tagInfo.digest = digest
                return applyAttributesFromContent(manifestBytes, jsonBytes, manifestMetadata)
            } catch (exception: IOException) {
                logger.error("Unable to deserialize the manifest.json file: [$exception]")
                throw RuntimeException(exception)
            }
        }

        private fun applyAttributesFromContent(manifestBytes: ByteArray, jsonBytes: ByteArray, manifestMetadata: ManifestMetadata): ManifestMetadata {
            val config = JsonUtils.objectMapper.readTree(jsonBytes)
            val manifest = JsonUtils.objectMapper.readTree(manifestBytes)
            var totalSize = 0L
            val history = config.get("history")
            val layers = manifest.get("layers")
            val historySize = history?.size() ?: 0
            var historyCounter = 0L
            if (history != null) {
                // TODO: resolve params pass
                val iterable = Iterable<JsonNode> { history.elements() }
                historyCounter = StreamSupport.stream(iterable.spliterator(), false).filter { notEmptyHistoryLayer(it) }.count()
            }
            val foreignHasHistory = layers.size().toLong() == historyCounter
            var iterationsCounter = 0
            var historyIndex = 0

            var layersIndex = 0
            while (historyIndex < historySize || layersIndex < layers.size()) {
                val historyLayer = history?.get(historyIndex)
                val layer = layers.get(layersIndex)
                var size = 0L
                var digest: String? = null
                if (notEmptyHistoryLayer(historyLayer) || !foreignHasHistory && isForeignLayer(layer)) {
                    size = layer.get(DOCKER_NODE_SIZE).asLong()
                    totalSize += size
                    digest = layer.get(DOCKER_DIGEST).asText()
                    ++layersIndex
                }

                if (!isForeignLayer(layer) || foreignHasHistory) {
                    ++historyIndex
                }

                var created = config.get("created").asText()
                if (historyLayer != null && !isForeignLayer(layer)) {
                    created = historyLayer["created"].asText()
                }

                val blobInfo = DockerBlobInfo(EMPTYSTR, digest, size, created)
                if (!isForeignLayer(layer)) {
                    populateWithCommand(historyLayer, blobInfo)
                }

                populateWithMediaType(layer, blobInfo)
                manifestMetadata.blobsInfo.add(blobInfo)
//                if (historyIndex == historySize && layersIndex == layers.size()) {
//                    breakeCircuit(manifestBytes, jsonBytes, "Loop Indexes not Incing")
//                }
                checkCircuitBreaker(manifestBytes, jsonBytes, iterationsCounter)
                ++iterationsCounter
            }
            manifestMetadata.blobsInfo.reverse()
            manifestMetadata.tagInfo.totalSize = totalSize
            val dockerMetadata = JsonUtils.objectMapper.readValue(config.toString().toByteArray(), DockerImageMetadata::class.java)
            ManifestUtil.populatePorts(manifestMetadata, dockerMetadata)
            ManifestUtil.populateVolumes(manifestMetadata, dockerMetadata)
            ManifestUtil.populateLabels(manifestMetadata, dockerMetadata)
            return manifestMetadata
        }

        private fun checkCircuitBreaker(manifestBytes: ByteArray, jsonBytes: ByteArray, iterationsCounter: Int) {
            if (iterationsCounter > CIRCUIT_BREAKER_THRESHOLD) {
                breakCircuit(manifestBytes, jsonBytes, "$CIRCUIT_BREAKER_THRESHOLD Iterations ware performed")
            }
        }

        private fun breakCircuit(manifestBytes: ByteArray, jsonBytes: ByteArray, reason: String) {
            val msg = "ManifestSchema2Deserializer CIRCUIT BREAKER: " + reason + " breaking operation.\nManifest: " + String(manifestBytes, StandardCharsets.UTF_8) + "\njsonBytes:" + String(jsonBytes, StandardCharsets.UTF_8)
            logger.error(msg)
            throw IllegalArgumentException("Circuit Breaker Threshold Reached, Breaking Operation. see log output for manifest details.")
        }

        private fun isForeignLayer(layer: JsonNode?): Boolean {
            return layer != null && layer.has("mediaType") && "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == layer.get("mediaType").asText()
        }

        private fun notEmptyHistoryLayer(historyLayer: JsonNode?): Boolean {
            return historyLayer != null && historyLayer.get("empty_layer") == null
        }

        private fun populateWithCommand(layerHistory: JsonNode?, blobInfo: DockerBlobInfo) {
            if (layerHistory != null && layerHistory.has("created_by")) {
                var command = layerHistory.get("created_by").asText()
                if (StringUtils.contains(command, "(nop)")) {
                    command = StringUtils.substringAfter(command, "(nop) ")
                    val dockerCmd = StringUtils.substringBefore(command.trim { it <= ' ' }, " ")
                    command = StringUtils.substringAfter(command, " ")
                    blobInfo.command = dockerCmd
                    blobInfo.commandText = command
                } else if (StringUtils.isNotBlank(command)) {
                    blobInfo.command = "RUN"
                    blobInfo.commandText = command
                }
            }
        }

        private fun populateWithMediaType(layerNode: JsonNode?, blobInfo: DockerBlobInfo) {
            if (layerNode != null) {
                if (layerNode.has("mediaType")) {
                    blobInfo.mediaType = layerNode.get("mediaType").asText()
                }

                if (layerNode.has("urls")) {
                    blobInfo.urls = mutableListOf()
                    layerNode.get("urls").forEach { jsonNode -> blobInfo.urls!!.add(jsonNode.asText()) }
                }
            }
        }
    }
}
