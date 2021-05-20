/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeDeleteRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo

interface ClusterNodeService {
    /**
     * 查询主节点信息
     */
    fun getMasterNodeInfo(): ClusterNodeInfo

    /**
     * 查询名称为[name]的节点信息
     */
    fun getClusterNodeInfo(name: String): ClusterNodeInfo?

    /**
     * 判断名称为[name]的集群节点是否存在
     */
    fun checkExist(name: String): Boolean

    /**
     * 根据[request]创建集群节点，创建成功后返回集群节点信息
     */
    fun createClusterNode(request: ClusterNodeCreateRequest): ClusterNodeInfo

    /**
     * 根据请求[clusterNodeDeleteRequest]删除集群节点
     */
    fun deleteClusterNode(clusterNodeDeleteRequest: ClusterNodeDeleteRequest)

    /**
     * 查询所有的集群节点
     */
    fun listClusterNode(name: String?, type: String?): List<ClusterNodeInfo>

    /**
     * 查询[set]列表里面的集群slave节点
     */
    fun listClusterNode(set: Set<String>): List<RemoteClusterInfo>

    /**
     * 查询[list]列表key里面的集群salve节点
     */
    fun listClusterNode(list: List<String>): List<RemoteClusterInfo>

    /**
     * 分页查询集群节点
     *
     * @param pageNumber 当前页
     * @param pageSize 分页数量
     * @param name 集群节点名称
     */
    fun listClusterNodePage(name: String?, type: String?, pageNumber: Int, pageSize: Int): Page<ClusterNodeInfo>

    /**
     * 查询集群节点详情
     *
     * @param name 集群节点名称
     */
    fun detailClusterNode(name: String): ClusterNodeInfo

    /**
     * 尝试连接远程集群，连接失败抛[ErrorCodeException]异常
     */
    fun tryConnect(it: String)
}
