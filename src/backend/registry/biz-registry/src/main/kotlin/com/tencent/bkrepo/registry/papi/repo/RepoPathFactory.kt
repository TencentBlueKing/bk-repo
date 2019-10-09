package com.tencent.bkrepo.registry.papi.repo

import com.tencent.bkrepo.registry.client.util.PathUtils
import java.lang.reflect.Constructor
import org.slf4j.LoggerFactory

class RepoPathFactory {
    companion object {
        private val log = LoggerFactory.getLogger(RepoPathFactory::class.java)
        private var ctor: Constructor<*>? = null

        fun create(repoKey: String, path: String): RepoPath {
            try {
                return ctor!!.newInstance(repoKey, path) as RepoPath
            } catch (var3: Exception) {
                throw RuntimeException("Could not create repoPath.", var3)
            }
        }

        fun create(rpp: String?): RepoPath {
            var rpp = rpp
            if (rpp != null && rpp.length != 0) {
                rpp = PathUtils.trimLeadingSlashes(PathUtils.formatPath(rpp))
                val idx = rpp!!.indexOf('/')
                val repoKey: String
                val path: String
                if (idx < 0) {
                    repoKey = rpp
                    path = ""
                } else {
                    repoKey = rpp.substring(0, idx)
                    path = rpp.substring(idx + 1)
                }

                return create(repoKey, path)
            } else {
                throw IllegalArgumentException("Path cannot be empty.")
            }
        }

        init {
            try {
                val clazz = RepoPathFactory::class.java.classLoader.loadClass("org.artifactory.model.common.RepoPathImpl")
                ctor = clazz.getConstructor(String::class.java, String::class.java)
            } catch (var1: Exception) {
                log.error("Error creating the repoPath factory.", var1)
            }
        }
    }
}
