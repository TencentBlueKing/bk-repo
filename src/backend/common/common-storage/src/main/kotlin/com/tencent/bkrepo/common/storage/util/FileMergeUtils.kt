package com.tencent.bkrepo.common.storage.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 文件合并工具
 */
object FileMergeUtils {

    /**
     * 按files里面的顺序合并成一个文件
     * @param files 要合并的文件列表
     * @param outputFile 合并后输出的文件
     */
    @Throws(IOException::class)
    fun mergeFiles(files: List<File>?, outputFile: File) {
        if (files == null || files.isEmpty()) {
            throw IOException("no file need to merge")
        }

        if (!outputFile.exists()) {
            val createNewFile = outputFile.createNewFile()
            if (!createNewFile) {
                throw IOException("create file $outputFile fail!")
            }
        }

        FileOutputStream(outputFile).channel.use { outChannel ->
            files.forEach { file ->
                FileInputStream(file).channel.use { inChannel ->
                    inChannel.transferTo(0, inChannel.size(), outChannel)
                }
            }
        }
    }

}