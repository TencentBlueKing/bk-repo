package com.tencent.bkrepo.media.live.service

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * HLS协议服务
 * 提供HLS播放列表（m3u8）和分片文件（TS）的访问
 * 仅支持 TS (MPEG-TS) 格式
 */
@Service
class HlsService(
    private val nodeService: NodeService,
) : ArtifactService() {

    /**
     * 分片列表缓存
     * Key: "$projectId|$repoName|$resolution"
     * Value: List<NodeInfo>
     * 缓存时间：1秒（HLS直播场景下分片更新频繁，需要较短的缓存时间）
     */
    private val segmentsCache: LoadingCache<String, List<NodeInfo>> = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_CACHE_SIZE)
        .expireAfterWrite(DEFAULT_CACHE_DURATION_SECONDS, TimeUnit.SECONDS)
        .build(object : CacheLoader<String, List<NodeInfo>>() {
            override fun load(key: String): List<NodeInfo> {
                val (projectId, repoName, resolution) = parseCacheKey(key)
                return loadSegments(projectId, repoName, resolution)
            }
        })

    /**
     * 获取HLS播放列表（m3u8文件）- 直播模式
     * 仅支持 TS 格式
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param resolution 分辨率
     * @param segmentDuration 每个分片时长（秒，默认1）
     * @return m3u8播放列表内容
     */
    fun getPlaylist(
        projectId: String,
        repoName: String,
        resolution: String,
        segmentDuration: Double = 1.0,
    ): String {
        // 获取实际存在的分片文件（仅支持 .ts 格式）
        val segments = getSegments(projectId, repoName, resolution)

        if (segments.isEmpty() || segments.size < DEFAULT_MAX_SEGMENTS) {
            logger.info("No enough segments found for stream [$projectId/$repoName/live]")
            return buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-VERSION:3")
                appendLine("#EXT-X-TARGETDURATION:${segmentDuration.toInt()}")
                appendLine("#EXT-X-MEDIA-SEQUENCE:0")
                appendLine("#EXT-X-ENDLIST")
            }
        }

        // 计算MEDIA-SEQUENCE（第一个分片的序号）
        val mediaSequence = segments.first().metadata?.get("EXT-X-MEDIA-SEQUENCE") ?: 0
        val targetDuration = segmentDuration.toInt().coerceAtLeast(1)

        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:$targetDuration")
            appendLine("#EXT-X-MEDIA-SEQUENCE:$mediaSequence")

            // 添加分片列表
            segments.forEach { segment ->
                appendLine("#EXTINF:$segmentDuration,")
                appendLine(
                    "/web/media-live/api/hls/$projectId/$repoName${segment.fullPath}"
                )
                // 最后一个分片标记直播结束
                if (segment.metadata?.get("EXT-X-ENDLIST")?.toString()?.toBoolean() == true) {
                    appendLine("#EXT-X-ENDLIST")
                }
            }
        }

        logger.debug("Generated HLS live playlist for stream [$projectId/$repoName], segments: ${segments.size}")
        return playlist
    }

    /**
     * 获取分片文件列表（带缓存）
     * 仅支持 .ts (TS) 格式
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param resolution 分辨率
     * @return 分片文件列表（按文件名排序，最新的在前）
     */
    private fun getSegments(
        projectId: String,
        repoName: String,
        resolution: String,
    ): List<NodeInfo> {
        val cacheKey = generateCacheKey(projectId, repoName, resolution)
        return try {
            segmentsCache[cacheKey]
        } catch (e: Exception) {
            logger.warn("Failed to get segments from cache for key [$cacheKey], loading directly", e)
            loadSegments(projectId, repoName, resolution)
        }
    }

    /**
     * 加载分片文件列表（实际数据加载逻辑）
     * 仅支持 .ts (TS) 格式
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param resolution 分辨率
     * @return 分片文件列表（按文件名排序，最新的在前）
     */
    private fun loadSegments(
        projectId: String,
        repoName: String,
        resolution: String,
    ): List<NodeInfo> {
        return try {
            val fullPath = PathUtils.combineFullPath(LIVE_FOLDER, resolution)
            val artifactInfo = ArtifactInfo(projectId, repoName, fullPath)
            val option = NodeListOption(
                pageNumber = 1,
                pageSize = DEFAULT_MAX_SEGMENTS * 2,
                includeFolder = false,
                deep = false,
                includeMetadata = true,
                sortProperty = listOf(TNode::createdDate.name),
                direction = listOf(Sort.Direction.DESC.name)
            )
            val data = nodeService.listNodePage(artifactInfo, option)
            if (data.totalPages > 1) {
                deleteOldSegments(data.records.last())
            }
            val nodes = data.records
                .filter { it.name.endsWith(".ts", ignoreCase = true) }
                .sortedBy { extractTimestamp(it.name) ?: 0 }
                .takeLast(DEFAULT_MAX_SEGMENTS)

            logger.debug("Found ${nodes.size} TS segments in [$fullPath]")

            if (nodes.size < DEFAULT_MAX_SEGMENTS || containsOldSegment(nodes)) {
                emptyList()
            } else {
                nodes
            }
        } catch (e: Exception) {
            logger.error("Failed to list segments for [$LIVE_FOLDER]", e)
            emptyList()
        }
    }

    private fun deleteOldSegments(nodeInfo: NodeInfo) {
        with(nodeInfo) {
            executor.execute {
                nodeService.deleteBeforeDate(
                    projectId = projectId,
                    repoName = repoName,
                    date = LocalDateTime.parse(createdDate),
                    operator = SYSTEM_USER,
                    path = PathUtils.resolveParent(fullPath),
                    decreaseVolume = false
                )
            }
        }
    }

    /**
     * 检查最后一个片段的创建时间和当前时间相差不超过10秒
     * 不包含上次的结束片段
     */
    private fun containsOldSegment(nodes: List<NodeInfo>): Boolean {
        if (nodes.isNotEmpty()) {
            val lastNode = nodes.last()
            val createdDate = lastNode.createdDate
            try {
                val createdDateTime = LocalDateTime.parse(createdDate, DateTimeFormatter.ISO_DATE_TIME)
                val currentDateTime = LocalDateTime.now()
                val secondsDiff = ChronoUnit.SECONDS.between(createdDateTime, currentDateTime)
                if (secondsDiff > 10) {
                    logger.info("Last segment created time [$createdDate] is more than 10 seconds ago")
                    return true
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse createdDate [$createdDate] for last segment", e)
            }
        }

        val endListIndex = nodes.indexOfFirst { segment ->
            segment.metadata?.get("EXT-X-ENDLIST")?.toString()?.toBoolean() == true
        }

        return endListIndex >= 0 && endListIndex < nodes.size - 1
    }

    /**
     * 生成缓存键
     */
    private fun generateCacheKey(projectId: String, repoName: String, resolution: String): String {
        return "$projectId|$repoName|$resolution"
    }

    /**
     * 解析缓存键
     */
    private fun parseCacheKey(key: String): Triple<String, String, String> {
        val parts = key.split("|")
        require(parts.size == 3) { "Invalid cache key format: $key" }
        return Triple(parts[0], parts[1], parts[2])
    }

    /**
     * 从文件名中提取时间戳
     */
    private fun extractTimestamp(fileName: String): Long? {
        val pattern = Pattern.compile("(\\d+)")
        val matcher = pattern.matcher(fileName)
        return if (matcher.find()) {
            matcher.group(1)?.toLongOrNull()
        } else {
            null
        }
    }

    /**
     * 获取HLS分片文件（仅支持 TS 格式）
     * @param artifactInfo 文件信息
     */
    fun getSegment(artifactInfo: ArtifactInfo) {
        val context = ArtifactDownloadContext(artifact = artifactInfo)
        repository.download(context)
        logger.debug("Download HLS segment [${artifactInfo.getArtifactFullPath()}] (format: TS)")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HlsService::class.java)
        private const val LIVE_FOLDER = "/live"
        private const val DEFAULT_CACHE_SIZE = 1000L
        private const val DEFAULT_CACHE_DURATION_SECONDS = 1L
        private const val DEFAULT_MAX_SEGMENTS = 3
        private val executor = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            1,
            TimeUnit.MINUTES,
            ArrayBlockingQueue(0),
            ThreadFactoryBuilder().setNameFormat("hls-delete-thread-%d").build(),
            ThreadPoolExecutor.DiscardPolicy()
        ).trace()
    }
}