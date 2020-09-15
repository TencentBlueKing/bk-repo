import Vue from 'vue'

export default {
    // generic查询
    genericSearch (_, { projectId, repoName, name, stageTag = '', current = 1, limit = 15 }) {
        return Vue.prototype.$ajax.post(
            `repository/api/node/query`,
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
                            value: 'GENERIC',
                            operation: 'EQ'
                        },
                        {
                            field: 'folder',
                            value: false,
                            operation: 'EQ'
                        },
                        ...(name ? [
                            {
                                field: 'name',
                                value: `\*${name}\*`,
                                operation: 'MATCH'
                            }
                        ] : []),
                        ...(stageTag ? [
                            {
                                field: 'stageTag',
                                value: stageTag,
                                operation: stageTag === '@prerelease' ? 'PREFIX' : 'SUFFIX'
                            }
                        ] : [])
                    ],
                    relation: 'AND'
                }
            }
        )
    }
}
