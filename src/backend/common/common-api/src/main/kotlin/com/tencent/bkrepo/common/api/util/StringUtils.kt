package com.tencent.bkrepo.common.api.util

import com.tencent.bkrepo.common.api.constant.StringPool
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

private val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomString(size: Int) = List(size) { alphabet.random() }.joinToString("")

fun uniqueId() = UUID.randomUUID().toString().replace(StringPool.DASH, StringPool.EMPTY).toLowerCase()

fun String.toPath(): Path = Paths.get(this)
