package com.tencent.bkrepo.common.mongo.routing

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** §3.19.2 / G-34：扫描源码，检出非白名单模块直连 Default `node_*`。 */
object NodeDirectMongoAuditor {

    fun audit(backendRoot: Path = locateBackendRoot()): List<String> {
        val violations = mutableListOf<String>()
        Files.walk(backendRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .forEach { file ->
                    val relative = backendRoot.relativize(file).toString().replace('\\', '/')
                    if (isAllowlisted(relative)) return@forEach
                    if (relative.endsWith("Test.kt")) return@forEach
                    val text = file.readText()
                    if (isRoutingAware(text)) return@forEach
                    if (!hasDirectNodeMongoUsage(text)) return@forEach
                    violations += relative
                }
        }
        return violations
    }

    private fun isAllowlisted(relativePath: String): Boolean =
        ALLOWLIST.any { relativePath.contains(it) }

    private fun isRoutingAware(source: String): Boolean =
        ROUTING_MARKERS.any { source.contains(it) }

    private fun hasDirectNodeMongoUsage(source: String): Boolean =
        source.lines().any { line ->
            MONGO_OP_PATTERN.containsMatchIn(line) && referencesNodeCollection(line)
        }

    private fun referencesNodeCollection(line: String): Boolean =
        NODE_COLLECTION_REF.containsMatchIn(line)

    private fun locateBackendRoot(): Path {
        var dir = Paths.get("").toAbsolutePath()
        for (i in 0 until 6) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) return dir
            dir = dir.parent ?: return dir
        }
        return dir
    }

    private val ALLOWLIST = listOf(
        "common-mongo/",
        "common-metadata/metadata-service/src/main/kotlin/com/tencent/bkrepo/common/metadata/routing/",
        "common-metadata/metadata-service/src/main/kotlin/com/tencent/bkrepo/common/metadata/dao/node/",
        "common-mongo/src/test/",
        "metadata-service/src/test/",
        "NodeDirectMongoAudit",
    )

    private val MONGO_OP_PATTERN =
        Regex("""mongoTemplate\.(find|findOne|insert|save|update|remove|count|aggregate)\(""")

    private val NODE_COLLECTION_REF = Regex(
        """["']node_\d|["']node_"\s*\+|node_\$\{|COLLECTION_(NAME_)?PREFIX|COLLECTION_NODE""",
        RegexOption.IGNORE_CASE,
    )

    private val ROUTING_MARKERS = listOf(
        "NodeShardReadSupport",
        "NodeMongoOperations",
        "NodeBatchQueryHelper",
        "NodeScatterQueryService",
        "buildNodeScanGroups",
        "MongoDbBatchJob",
        "DefaultContextMongoDbJob",
        "routingRegistry",
        "NodeRoutingContext",
        "NodeIterator",
    )
}
