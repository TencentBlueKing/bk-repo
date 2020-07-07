package com.tencent.bkrepo.common.storage.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun String.toPath(): Path = Paths.get(this)

fun Path.createFile(): File {
    if (!Files.isRegularFile(this)) {
        if (this.parent != null) {
            Files.createDirectories(this.parent)
        }
        Files.createFile(this)
    }
    return this.toFile()
}
