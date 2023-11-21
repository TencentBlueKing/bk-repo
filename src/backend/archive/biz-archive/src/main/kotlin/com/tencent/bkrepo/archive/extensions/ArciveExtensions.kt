package com.tencent.bkrepo.archive.extensions

import com.tencent.bkrepo.archive.model.TArchiveFile

fun TArchiveFile.key(): String {
    return "$sha256/$storageCredentialsKey"
}
