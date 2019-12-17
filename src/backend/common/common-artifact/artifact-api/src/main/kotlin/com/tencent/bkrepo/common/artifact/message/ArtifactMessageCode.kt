package com.tencent.bkrepo.common.artifact.message

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 构件相关错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

enum class ArtifactMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    PROJECT_NOT_FOUND(1, "artifact.project.notfound"),
    PROJECT_EXISTED(2, "artifact.project.existed"),
    REPOSITORY_NOT_FOUND(3, "artifact.repository.notfound"),
    REPOSITORY_EXISTED(4, "artifact.repository.existed"),
    NODE_NOT_FOUND(5, "artifact.node.notfound"),
    NODE_PATH_INVALID(6, "artifact.node.path.invalid"),
    NODE_EXISTED(7, "artifact.node.existed"),
    NODE_CONFLICT(8, "artifact.node.conflict");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 10
}
