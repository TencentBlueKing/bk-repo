package com.tencent.bkrepo.registry.common.repomd

import com.google.common.collect.Maps
import com.tencent.bkrepo.registry.repomd.WorkContext
import java.nio.file.Path

abstract class ArtifactoryWorkContext(contextPath: String) : WorkContext {

    lateinit var contextMap: MutableMap<String, Any>
    private var contextPath: String = ""
    lateinit var tempDir: Path
//  lateinit var   repo: Repo

    init {
        this.contextMap = Maps.newHashMap()
        this.contextPath = contextPath
//        this.tempDir = ArtifactoryHome.get().getTempWorkDir().toPath()
//        this.contextMap["auth"] = LinkedList()
    }

    override fun getContextPath(): String {
        return this.contextPath
    }
}
