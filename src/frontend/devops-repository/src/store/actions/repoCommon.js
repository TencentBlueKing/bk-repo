import Vue from 'vue'

const prefix = 'repository/api'

export default {
    // 分页查询包列表
    getPackageList (_, { projectId, repoName, packageName, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/package/page/${projectId}/${repoName}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    packageName
                }
            }
        )
    },
    // 删除包
    deletePackage (_, { projectId, repoType, repoName, packageKey }) {
        return Vue.prototype.$ajax.delete(
            `${repoType}/ext/package/delete/${projectId}/${repoName}`,
            {
                params: {
                    packageKey
                }
            }
        )
    },
    // 查询包信息
    getPackageInfo (_, { projectId, repoName, packageKey }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/package/info/${projectId}/${repoName}`,
            {
                params: {
                    packageKey
                }
            }
        )
    },
    // 查询包版本列表
    getVersionList (_, { projectId, repoName, packageKey, version, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/version/page/${projectId}/${repoName}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    packageKey,
                    version
                }
            }
        )
    },
    // 删除包版本
    deleteVersion (_, { projectId, repoType, repoName, packageKey, version }) {
        return Vue.prototype.$ajax.delete(
            `${repoType}/ext/version/delete/${projectId}/${repoName}`,
            {
                params: {
                    packageKey,
                    version
                }
            }
        )
    },
    // 查询包版本详情
    getVersionDetail (_, { projectId, repoType, repoName, packageKey, version }) {
        return Vue.prototype.$ajax.get(
            `${repoType}/ext/version/detail/${projectId}/${repoName}`,
            {
                params: {
                    packageKey,
                    version
                }
            }
        )
    },
    // npm查询被依赖列表
    getNpmDependents (_, { projectId, repoName, packageKey, current = 1 }) {
        return Vue.prototype.$ajax.get(
            `npm/ext/dependent/page/${projectId}/${repoName}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: 20,
                    packageKey
                }
            }
        )
    },
    // 跨仓库搜索包
    searchPackageList (_, { projectId, repoType, packageName, current = 1, limit = 20 }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/package/search`,
            {
                page: {
                    pageNumber: current,
                    pageSize: limit
                },
                sort: {
                    properties: ['name'],
                    direction: 'ASC'
                },
                rule: {
                    rules: [
                        {
                            field: 'projectId',
                            value: projectId,
                            operation: 'EQ'
                        },
                        {
                            field: 'repoType',
                            value: repoType.toUpperCase(),
                            operation: 'EQ'
                        },
                        {
                            field: 'name',
                            value: '*' + packageName + '*',
                            operation: 'MATCH'
                        }
                    ],
                    relation: 'AND'
                }
            }
        )
    }
}
