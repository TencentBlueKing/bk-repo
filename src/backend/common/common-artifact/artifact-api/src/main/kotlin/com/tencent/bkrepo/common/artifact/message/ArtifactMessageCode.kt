package com.tencent.bkrepo.common.artifact.message

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 构件相关错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

enum class ArtifactMessageCode(private val key: String) : MessageCode {
    PROJECT_NOT_FOUND("artifact.project.notfound"),
    PROJECT_EXISTED("artifact.project.existed"),
    REPOSITORY_NOT_FOUND("artifact.repository.notfound"),
    REPOSITORY_EXISTED("artifact.repository.existed"),
    REPOSITORY_CONTAINS_FILE("artifact.repository.contains-file"),
    NODE_NOT_FOUND("artifact.node.notfound"),
    NODE_PATH_INVALID("artifact.node.path.invalid"),
    NODE_EXISTED("artifact.node.existed"),
    NODE_CONFLICT("artifact.node.conflict"),
    NODE_LIST_TOO_LARGE("artifact.node.list.too-large");

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 10
}
