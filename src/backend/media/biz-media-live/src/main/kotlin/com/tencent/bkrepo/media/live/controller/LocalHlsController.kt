package com.tencent.bkrepo.media.live.controller

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.io.File

@RestController
@RequestMapping("/local/hls")
class LocalHlsController {

    companion object {
        private val logger = LoggerFactory.getLogger(LocalHlsController::class.java)
        private const val DEFAULT_HLS_SEGMENT_TIME = 1
    }

    private val localDir = "C:\\"


    @RequestMapping(
        value = ["/{resolution}/playlist.m3u8"],
        method = [RequestMethod.GET, RequestMethod.OPTIONS]
    )
    fun getHlsPlaylist(
        @PathVariable resolution: String,
    ): ResponseEntity<String> {
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:$DEFAULT_HLS_SEGMENT_TIME")
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")

            // 读取 localDir 中的 ts 文件
            val tsFiles = getTsFiles(PathUtils.combineFullPath(localDir, resolution))
            logger.info("Found [${tsFiles.size}] TS files in directory [$localDir]")

            // 按文件名排序（通常按时间顺序）
            tsFiles.sortedBy { it.name }.forEach { tsFile ->
                appendLine("#EXTINF:$DEFAULT_HLS_SEGMENT_TIME.0,")
                appendLine("${tsFile.name}")
            }

            // 如果是点播模式，添加结束标记
            if (tsFiles.isNotEmpty()) {
                appendLine("#EXT-X-ENDLIST")
            }
        }
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(playlist)
    }

    /**
     * 获取指定目录下的所有 .ts 文件
     * @param directory 目录路径
     * @return .ts 文件列表
     */
    private fun getTsFiles(directory: String): List<File> {
        return try {
            val dir = File(directory)
            if (!dir.exists() || !dir.isDirectory) {
                logger.warn("Directory [$directory] does not exist or is not a directory")
                return emptyList()
            }
            dir.listFiles { _, name -> name.lowercase().endsWith(".ts") }?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to read TS files from directory [$directory]", e)
            emptyList()
        }
    }

    /**
     * 获取HLS分片文件（仅支持 TS 格式）
     */
    @RequestMapping(
        value = ["/{resolution}/{name}"],
        method = [RequestMethod.GET, RequestMethod.OPTIONS]
    )
    fun getHlsSegment(
        @PathVariable resolution: String,
        @PathVariable name: String,
    )  {
        val file = File("$localDir\\$resolution\\$name")
        if (!file.exists() || !file.isFile) {
            logger.warn("HLS segment file [$file] does not exist")
            throw RuntimeException("HLS segment file [$name] not found")
        }

        val response = HttpContextHolder.getResponse()
        response.apply {
            reset()
            contentType = "video/mp4"
            setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            outputStream.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }

    }
}