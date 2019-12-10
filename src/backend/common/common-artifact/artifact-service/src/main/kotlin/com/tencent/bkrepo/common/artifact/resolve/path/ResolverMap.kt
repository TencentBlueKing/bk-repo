package com.tencent.bkrepo.common.artifact.resolve.path

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.exception.ArtifactResolveException
import kotlin.reflect.KClass

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class ResolverMap : LinkedHashMap<KClass<out ArtifactInfo>, ArtifactInfoResolver>() {

    private lateinit var defaultResolver: ArtifactInfoResolver

    fun getResolver(key: KClass<out ArtifactInfo>): ArtifactInfoResolver {
        return super.get(key) ?: run {
            if (this::defaultResolver.isInitialized) {
                defaultResolver
            } else {
                throw ArtifactResolveException("Cannot find property artifact resolver.")
            }
        }
    }

    fun register(key: KClass<out ArtifactInfo>, resolver: ArtifactInfoResolver, default: Boolean) {
        if (default) {
            this.defaultResolver = resolver
        }
        super.put(key, resolver)
    }
}
