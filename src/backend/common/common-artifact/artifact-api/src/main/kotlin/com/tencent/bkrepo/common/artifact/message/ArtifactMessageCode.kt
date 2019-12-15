package com.tencent.bkrepo.common.artifact.message

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 构件相关错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

enum class ArtifactMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    REPOSITORY_NOT_FOUND(1, "artifact.repository.notfound"),
    REPOSITORY_EXIST(2, "artifact.repository.existed"),
    NODE_NOT_FOUND(3, "artifact.node.notfound"),
    NODE_PATH_INVALID(4, "artifact.node.path.invalid"),
    NODE_EXIST(5, "artifact.node.existed"),
    NODE_CONFLICT(6, "artifact.node.conflict");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 10
}
