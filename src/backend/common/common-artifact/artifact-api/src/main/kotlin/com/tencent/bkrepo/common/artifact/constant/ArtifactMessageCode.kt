package com.tencent.bkrepo.common.artifact.constant

/**
 * 构件相关错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

object ArtifactMessageCode {
    const val REPOSITORY_NOT_FOUND = 2501001 // 仓库{0}不存在
    const val NODE_NOT_FOUND = 2501002 // 节点{0}不存在
    const val NODE_PATH_INVALID = 2501003 // 节点路径{0}非法
    const val FOLDER_CANNOT_BE_MODIFIED = 2501004 // 文件夹不能被覆盖
    const val NODE_IS_EXIST = 2501005 // 节点{0}已经存在
    const val SHA256_CHECK_FAILED = 2501005 // sha256校验失败失
}
