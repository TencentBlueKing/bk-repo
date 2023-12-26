package com.tencent.bkrepo.repository.service.node

import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUnCompressedRequest

/**
 * 节点压缩操作
 * */
interface NodeCompressOperation {
    /**
     * 压缩节点
     * */
    fun compressedNode(nodeCompressedRequest: NodeCompressedRequest)

    /**
     * 解压节点
     * */
    fun uncompressedNode(nodeUnCompressedRequest: NodeUnCompressedRequest)
}
