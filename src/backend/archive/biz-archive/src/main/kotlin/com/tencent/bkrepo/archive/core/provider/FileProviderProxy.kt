package com.tencent.bkrepo.archive.core.provider

abstract class FileProviderProxy<T>(private val provider: FileProvider<T>) : FileProvider<T> {
    override fun key(param: T): String {
        return provider.key(param)
    }
}
