import Vue from 'vue'

const prefix = 'replication/api'

export default {
    // 查询集群节点
    getClusterList ({ commit }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/cluster/list`
        ).then(clusterList => {
            commit('SET_CLUSTER_LIST', clusterList)
        })
    },
    checkNodeName (_, { name }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/cluster/exist`,
            {
                params: {
                    name
                }
            }
        )
    },
    // 创建集群节点
    createCluster (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/cluster/create`,
            body
        )
    },
    // 更新集群节点
    updateCluster (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/cluster/update`,
            body
        )
    },
    // 删除集群节点
    deleteCluster (_, { id }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/cluster/delete/${id}`
        )
    },
    // 查询分发计划
    getPlanList (_, { projectId, name, enabled, lastExecutionStatus, sortType = 'CREATED_TIME', sortDirection, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/page/${projectId}`,
            {
                params: {
                    name,
                    enabled,
                    sortType,
                    sortDirection,
                    lastExecutionStatus,
                    pageNumber: current,
                    pageSize: limit
                }
            }
        )
    },
    // 创建分发计划
    createPlan (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/task/create`,
            body
        )
    },
    // 能否编辑分发计划
    checkUpdatePlan (_, { key }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/canUpdated/${key}`
        )
    },
    // 编辑分发计划
    updatePlan (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/task/update`,
            body
        )
    },
    // 启用/停用计划
    changeEnabled (_, { key }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/task/toggle/status/${key}`
        )
    },
    // 执行计划
    executePlan (_, { key }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/task/execute/${key}`
        )
    },
    // 复制计划
    copyPlan (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/task/copy`,
            body
        )
    },
    // 计划详情
    getPlanDetail (_, { key }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/detail/${key}`
        )
    },
    // 删除计划
    deletePlan (_, { key }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/task/delete/${key}`
        )
    },
    // 计划执行日志
    getPlanLogList (_, { key, status, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/record/page/${key}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    status
                }
            }
        )
    },
    // 计划执行日志
    getPlanLogDetail (_, { id }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/record/${id}`
        )
    },
    // 计划执行日志制品详情
    getPlanLogPackageList (_, { id, status, packageName, repoName, clusterName, path, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/task/record/detail/page/${id}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    status: status || undefined,
                    packageName: packageName || undefined,
                    repoName: repoName || undefined,
                    clusterName: clusterName || undefined,
                    path: path || undefined
                }
            }
        )
    }
}
