import Vue from 'vue'

const prefix = 'repository/api'

export default {
    // 请求文件夹下的子文件夹
    getFolderList ({ commit }, { projectId, repoName, roadMap, fullPath = '' }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/query`,
            {
                page: {
                    pageNumber: 1,
                    pageSize: 1000
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
                            field: 'repoName',
                            value: repoName,
                            operation: 'EQ'
                        },
                        {
                            field: 'path',
                            value: `${fullPath === '/' ? '' : fullPath}/`,
                            operation: 'EQ'
                        },
                        {
                            field: 'folder',
                            value: true,
                            operation: 'EQ'
                        }
                    ],
                    relation: 'AND'
                }
            }
        ).then(({ records }) => {
            commit('UPDATE_TREE', {
                roadMap,
                list: records.map((v, index) => ({
                    ...v,
                    roadMap: `${roadMap},${index}`
                }))
            })
        })
    },
    // 请求文件/文件夹详情
    getNodeDetail (_, { projectId, repoName, fullPath = '' }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/node/detail/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    },
    // 请求文件夹下的文件夹及制品
    getArtifactoryList (_, { projectId, repoName, fullPath, current, limit, includeFolder = true, includeMetadata = true, deep = false }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/node/page/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    includeFolder,
                    includeMetadata,
                    deep,
                    sort: true
                }
            }
        )
    },
    // 仓库内自定义查询
    getArtifactoryListByQuery (_, { projectId, repoName, name, current = 1, limit = 15 }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/query`,
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
                            field: 'repoName',
                            value: repoName,
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
                        ] : [])
                    ],
                    relation: 'AND'
                }
            }
        )
    },
    // 创建目录
    createFolder (_, { projectId, repoName, fullPath = '' }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/mkdir/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    },
    // 重命名
    renameNode (_, { projectId, repoName, fullPath, newFullPath }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/rename/${projectId}/${repoName}/${encodeURIComponent(fullPath)}?newFullPath=${encodeURIComponent(newFullPath)}`
        )
    },
    // 移动
    moveNode (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/move`,
            body
        )
    },
    // 拷贝
    copyNode (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/copy`,
            body
        )
    },
    // 上传文件
    uploadArtifactory (_, { xhr, projectId, repoName, body, progressHandler, fullPath = '', headers = {} }) {
        return new Promise((resolve, reject) => {
            xhr.onreadystatechange = () => {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        resolve(xhr.response)
                    } else {
                        reject(xhr.response)
                    }
                }
            }
            xhr.upload.addEventListener('progress', progressHandler)
            xhr.open('PUT', `/generic/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`, true)
            xhr.responseType = 'json'
            xhr.setRequestHeader('Content-Type', headers['Content-Type'])
            xhr.setRequestHeader('X-BKREPO-OVERWRITE', headers['X-BKREPO-OVERWRITE'])
            xhr.setRequestHeader('X-BKREPO-EXPIRES', headers['X-BKREPO-EXPIRES'])
            xhr.send(body)
        })
    },
    // 删除文件
    deleteArtifactory (_, { projectId, repoName, fullPath = '' }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/node/delete/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    },
    // 分享文件
    shareArtifactory (_, { projectId, repoName, body, fullPath = '' }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/share/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            body
        )
    },
    // 统计节点大小信息
    getFolderSize (_, { projectId, repoName, fullPath }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/node/size/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    }
}
