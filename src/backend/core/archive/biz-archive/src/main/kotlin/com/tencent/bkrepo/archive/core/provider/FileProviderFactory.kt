package com.tencent.bkrepo.archive.core.provider

import java.nio.file.Path
import java.time.Duration

object FileProviderFactory {

    fun <T> createBuilder(): Builder<T> {
        return Builder()
    }

    class Builder<T> {
        private lateinit var delegate: FileProvider<T>
        private var cached = false
        private var concurrent = false
        private var expire: Duration? = null
        private var cachePath: Path? = null
        fun from(provider: FileProvider<T>): Builder<T> {
            this.delegate = provider
            return this
        }

        fun enableCache(expire: Duration, cachePath: Path): Builder<T> {
            this.expire = expire
            this.cachePath = cachePath
            this.cached = true
            return this
        }

        fun concurrent(): Builder<T> {
            this.concurrent = true
            return this
        }

        fun build(): FileProvider<T> {
            if (cached) {
                delegate = CacheFileProviderProxy(delegate, expire!!, cachePath!!)
            }
            if (concurrent) {
                delegate = ConcurrentFileProvider(delegate)
            }
            return delegate
        }
    }
}
