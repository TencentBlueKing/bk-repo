package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.artifact.stream.ZeroInputStream
import org.springframework.util.unit.DataSize
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable

class StorageHealthChecker(dir: Path, private val dataSize: DataSize) : Callable<Unit> {

    private val tempPath = dir.resolve(System.nanoTime().toString())

    override fun call() {
        Files.createFile(tempPath)
        Files.newOutputStream(tempPath).use {
            ZeroInputStream(dataSize.toBytes()).copyTo(it)
        }
        Files.deleteIfExists(tempPath)
    }

    fun clean() {
        Files.deleteIfExists(tempPath)
    }
}
