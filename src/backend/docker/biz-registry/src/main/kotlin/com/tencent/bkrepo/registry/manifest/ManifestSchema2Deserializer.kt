package com.tencent.bkrepo.registry.manifest

import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.registry.util.JsonUtil
import com.tencent.bkrepo.registry.v2.model.DockerBlobInfo
import com.tencent.bkrepo.registry.v2.model.DockerDigest
import com.tencent.bkrepo.registry.v2.model.DockerImageMetadata
import com.tencent.bkrepo.registry.v2.model.ManifestMetadata
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.collections.Map.Entry
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory

class ManifestSchema2Deserializer {
    companion object {
        private val log = LoggerFactory.getLogger(ManifestSchema2Deserializer::class.java)
        private val CIRCUIT_BREAKER_THRESHOLD = 5000

        fun deserialize(manifestBytes: ByteArray, jsonBytes: ByteArray, dockerRepo: String, tag: String, digest: DockerDigest): ManifestMetadata {
            try {
                val manifestMetadata = ManifestMetadata()
                manifestMetadata.tagInfo.title = "$dockerRepo:$tag"
                manifestMetadata.tagInfo.digest = digest
                return applyAttributesFromContent(manifestBytes, jsonBytes, manifestMetadata)
            } catch (var6: IOException) {
                log.error("Unable to deserialize the manifest.json file: {}", var6.message, var6)
                throw RuntimeException(var6)
            }
        }

        @Throws(IOException::class)
        private fun applyAttributesFromContent(manifestBytes: ByteArray, jsonBytes: ByteArray, manifestMetadata: ManifestMetadata): ManifestMetadata {
            val config = JsonUtil.readTree(jsonBytes)
            val manifest = JsonUtil.readTree(manifestBytes)
            var totalSize = 0L
            val history = config.get("history")
            val layers = manifest.get("layers")
            val historySize = if (history == null) 0 else history!!.size()
            var historyCounter = 0L
            if (history != null) {
                val iterable = Iterable<JsonNode> { history.elements() }
                // TODO: resolve params pass
                // historyCounter = StreamSupport.stream(iterable.spliterator(), false).filter(Predicate<T> { notEmptyHistoryLayer(it) }).count()
            }

            val foreignHasHistory = layers.size() as Long == historyCounter
            var iterationsCounter = 0
            var historyIndex = 0

            var layersIndex = 0
            while (historyIndex < historySize || layersIndex < layers.size()) {
                val historyLayer = if (history == null) null else history!!.get(historyIndex)
                val layer = layers.get(layersIndex)
                var size = 0L
                var digest: String? = null
                if (notEmptyHistoryLayer(historyLayer) || !foreignHasHistory && isForeignLayer(layer)) {
                    size = layer.get("size").asLong()
                    totalSize += size
                    digest = layer.get("digest").asText()
                    ++layersIndex
                }

                if (!isForeignLayer(layer) || foreignHasHistory) {
                    ++historyIndex
                }

                var created = config.get("created").asText()
                if (historyLayer != null && !isForeignLayer(layer)) {
                    created = historyLayer!!["created"].asText()
                }

                val blobInfo = DockerBlobInfo("", digest, size, created)
                if (!isForeignLayer(layer)) {
                    populateWithCommand(historyLayer, blobInfo)
                }

                populateWithMediaType(layer, blobInfo)
                manifestMetadata.blobsInfo.add(blobInfo)
                if (historyIndex == historyIndex && layersIndex == layersIndex) {
                    breakeCircuit(manifestBytes, jsonBytes, "Loop Indexes not Incing")
                }

                checkCircuitBreaker(manifestBytes, jsonBytes, iterationsCounter)
                ++iterationsCounter
            }

            Collections.reverse(manifestMetadata.blobsInfo)
            manifestMetadata.tagInfo.totalSize = totalSize
            val dockerMetadata = JsonUtil.readValue(config, DockerImageMetadata::class.java) as DockerImageMetadata
            populatePorts(manifestMetadata, dockerMetadata)
            populateVolumes(manifestMetadata, dockerMetadata)
            populateLabels(manifestMetadata, dockerMetadata)
            return manifestMetadata
        }

        private fun checkCircuitBreaker(manifestBytes: ByteArray, jsonBytes: ByteArray, iterationsCounter: Int) {
            if (iterationsCounter > 5000) {
                breakeCircuit(manifestBytes, jsonBytes, "5000 Iterations ware performed")
            }
        }

        private fun breakeCircuit(manifestBytes: ByteArray, jsonBytes: ByteArray, reason: String) {
            val msg = "ManifestSchema2Deserializer CIRCUIT BREAKER: " + reason + " breaking operation.\nManifest: " + String(manifestBytes, StandardCharsets.UTF_8) + "\njsonBytes:" + String(jsonBytes, StandardCharsets.UTF_8)
            log.error(msg)
            throw IllegalArgumentException("Circuit Breaker Threshold Reached, Breaking Operation. see log output for manifest details.")
        }

        private fun isForeignLayer(layer: JsonNode?): Boolean {
            return layer != null && layer!!.has("mediaType") && "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip" == layer!!.get("mediaType").asText()
        }

        private fun notEmptyHistoryLayer(historyLayer: JsonNode?): Boolean {
            return historyLayer != null && historyLayer!!.get("empty_layer") == null
        }

        private fun populateWithCommand(layerHistory: JsonNode?, blobInfo: DockerBlobInfo) {
            if (layerHistory != null && layerHistory!!.has("created_by")) {
                var command = layerHistory!!.get("created_by").asText()
                if (StringUtils.contains(command, "(nop)")) {
                    command = StringUtils.substringAfter(command, "(nop) ")
                    val dockerCmd = StringUtils.substringBefore(command.trim({ it <= ' ' }), " ")
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
                if (layerNode!!.has("mediaType")) {
                    blobInfo.mediaType = layerNode!!.get("mediaType").asText()
                }

                if (layerNode!!.has("urls")) {
                    blobInfo.urls = mutableListOf<String>()
                    layerNode!!.get("urls").forEach { jsonNode -> blobInfo.urls!!.add(jsonNode.asText()) }
                }
            }
        }

        private fun populatePorts(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addPorts(manifestMetadata, dockerMetadata.config!!.exposedPorts)
            }

            if (dockerMetadata.containerConfig != null) {
                addPorts(manifestMetadata, dockerMetadata.containerConfig!!.exposedPorts)
            }
        }

        private fun addPorts(manifestMetadata: ManifestMetadata, exposedPorts: JsonNode?) {
            if (exposedPorts != null) {
                val iterPorts = exposedPorts.fieldNames()

                while (iterPorts.hasNext()) {
                    manifestMetadata.tagInfo.ports.add(iterPorts.next())
                }
            }
        }

        private fun populateVolumes(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addVolumes(manifestMetadata, dockerMetadata.config!!.volumes)
            }

            if (dockerMetadata.containerConfig != null) {
                addVolumes(manifestMetadata, dockerMetadata.containerConfig!!.volumes)
            }
        }

        private fun addVolumes(manifestMetadata: ManifestMetadata, volumes: JsonNode?) {
            if (volumes != null) {
                val iterVolume = volumes.fieldNames()

                while (iterVolume.hasNext()) {
                    manifestMetadata.tagInfo.volumes.add(iterVolume.next())
                }
            }
        }

        private fun populateLabels(manifestMetadata: ManifestMetadata, dockerMetadata: DockerImageMetadata) {
            if (dockerMetadata.config != null) {
                addLabels(manifestMetadata, dockerMetadata.config!!.labels)
            }

            if (dockerMetadata.containerConfig != null) {
                addLabels(manifestMetadata, dockerMetadata.containerConfig!!.labels)
            }
        }

        private fun addLabels(manifestMetadata: ManifestMetadata, labels: Map<String, String>?) {
            if (labels != null) {
                val var2 = labels.entries.iterator()

                while (var2.hasNext()) {
                    val label = var2.next() as Entry<String, String>
                    manifestMetadata.tagInfo.labels.put(label.key, label.value)
                }
            }
        }
    }
}
