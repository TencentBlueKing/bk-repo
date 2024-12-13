package com.tencent.bkrepo.job.backup.service.impl

import com.tencent.bkrepo.common.storage.core.locator.HashFileLocator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths

open class BaseService {

    /**
     * 生成随机文件路径
     * */
    fun generateRandomPath(sha256: String): String {
        val fileLocator = HashFileLocator()
        return fileLocator.locate(sha256)
    }


    /**
     * 生成随机文件路径
     * */
    fun generateRandomPath(root: Path, sha256: String): Path {
        return Paths.get(root.toFile().path, generateRandomPath(sha256))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseService::class.java)
        const val PROJECT_FILE_NAME = "project.json"
        const val REPOSITORY_FILE_NAME = "repository.json"
        const val NODE_FILE_NAME = "node.json"
        const val ZIP_FILE_SUFFRIX = ".zip"
        val FILE_LIST = listOf(PROJECT_FILE_NAME, REPOSITORY_FILE_NAME, NODE_FILE_NAME)
    }
}