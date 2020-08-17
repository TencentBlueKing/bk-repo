package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository

/**
 * 文件引用服务
 */
interface FileReferenceService {
    fun increment(node: TNode, repository: TRepository? = null): Boolean
    fun decrement(node: TNode, repository: TRepository? = null): Boolean
    fun increment(sha256: String, credentialsKey: String?): Boolean
    fun decrement(sha256: String, credentialsKey: String?): Boolean
    fun count(sha256: String, credentialsKey: String?): Long
}

