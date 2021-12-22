import Vue from 'vue'

export default {
    state: {
        permission: {
            write: false,
            edit: false,
            delete: false
        }
    },
    getters: {},
    mutations: {},
    actions: {
        // 分页查询仓库列表
        // override
        getRepoList (_, { projectId, current, limit, name, type }) {
            return Vue.prototype.$ajax.get(
                `repository/api/software/repo/page/${current}/${limit}`,
                {
                    params: {
                        projectId: projectId || undefined,
                        name: name || undefined,
                        type: type || undefined
                    }
                }
            )
        },
        // 包搜索-仓库数量
        // override
        searchRepoList (_, { projectId, repoType, packageName }) {
            const isGeneric = repoType === 'generic'
            return Vue.prototype.$ajax.get(
                `repository/api/software/${isGeneric ? 'node' : 'package'}/search/overview`,
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
        // override
        searchPackageList (_, { projectId, repoType, repoName, packageName, property = 'name', direction = 'ASC', current = 1, limit = 20 }) {
            const isGeneric = repoType === 'generic'
            return Vue.prototype.$ajax.post(
                `repository/api/software/${isGeneric ? 'node/search' : 'package/search'}`,
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
        }
    }
}
