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
    // 包搜索-仓库数量
    searchRepoList (_, { projectId, repoType, packageName }) {
        const isGeneric = repoType === 'generic'
        return Vue.prototype.$ajax.get(
            `${prefix}/${isGeneric ? 'node' : 'package'}/search/overview`,
            {
                params: {
                    projectId,
                    repoType: repoType.toUpperCase(),
                    [isGeneric ? 'name' : 'packageName']: '*' + packageName + '*'
                }
            }
        )
    },
    // 跨仓库搜索
    searchPackageList (_, { projectId, repoType, repoName, packageName, property = 'name', direction = 'ASC', current = 1, limit = 20 }) {
        const isGeneric = repoType === 'generic'
        return Vue.prototype.$ajax.post(
            `${prefix}/${isGeneric ? 'node/query' : 'package/search'}`,
            {
                page: {
                    pageNumber: current,
                    pageSize: limit
                },
                sort: {
                    properties: [property],
                    direction
                },
                rule: {
                    rules: [
                        ...(MODE_CONFIG === 'ci'
                            ? [{
                                field: 'repoName',
                                value: ['report', 'log'],
                                operation: 'NIN'
                            }]
                            : []),
                        ...(projectId
                            ? [{
                                field: 'projectId',
                                value: projectId,
                                operation: 'EQ'
                            }]
                            : []),
                        ...(repoType
                            ? [{
                                field: 'repoType',
                                value: repoType.toUpperCase(),
                                operation: 'EQ'
                            }]
                            : []),
                        ...(repoName
                            ? [{
                                field: 'repoName',
                                value: repoName,
                                operation: 'EQ'
                            }]
                            : []),
                        ...(packageName
                            ? [{
                                field: 'name',
                                value: '*' + packageName + '*',
                                operation: 'MATCH'
                            }]
                            : []),
                        ...(isGeneric
                            ? [{
                                field: 'folder',
                                value: false,
                                operation: 'EQ'
                            }]
                            : [])
                        
                    ],
                    relation: 'AND'
                }
            }
        )
    },
    // 获取docker域名
    getDockerDomain ({ commit }) {
        Vue.prototype.$ajax.get(
            'docker/ext/addr'
        ).then(domain => {
            commit('SET_DOMAIN', {
                type: 'docker',
                domain
            })
        })
    },
    // 获取npm域名
    getNpmDomain ({ commit }) {
        Vue.prototype.$ajax.get(
            'npm/ext/address'
        ).then(({ domain }) => {
            commit('SET_DOMAIN', {
                type: 'npm',
                domain: domain || `${location.origin}/npm`
            })
        })
    },

    // 制品晋级
    changeStageTag (_, { projectId, repoName, packageKey, version, tag }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/stage/upgrade/${projectId}/${repoName}`,
            null,
            {
                params: {
                    packageKey,
                    version,
                    tag
                }
            }
        )
    },
    // 添加元数据
    addPackageMetadata (_, { projectId, repoName, body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/metadata/package/${projectId}/${repoName}`,
            body
        )
    }
}
