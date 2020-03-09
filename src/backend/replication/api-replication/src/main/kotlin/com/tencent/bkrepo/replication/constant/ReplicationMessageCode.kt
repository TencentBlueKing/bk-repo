package com.tencent.bkrepo.replication.constant

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 通用文件错误码
 *
 * @author: carrypan
 * @date: 2019-10-11
 */

enum class ReplicationMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    REMOTE_CLUSTER_CONNECT_ERROR(1, "remote.cluster.connect.error");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 3
}
