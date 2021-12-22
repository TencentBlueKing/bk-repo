import Vue from 'vue'

const prefix = 'repository/api'

export default {
    // 查询文件夹下的所有文件数量（递归）
    getFileNumOfFolder ({ commit }, { projectId, repoName, fullPath = '/' }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/search`,
            {
                page: {
                    pageNumber: 1,
                    pageSize: 1
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
                            field: 'fullPath',
                            value: fullPath,
                            operation: 'PREFIX'
                        },
                        {
                            field: 'folder',
                            value: false,
                            operation: 'EQ'
                        }
                    ],
                    relation: 'AND'
                }
            }
        ).then(({ totalRecords }) => {
            return totalRecords
        })
    },
    // 请求文件夹下的子文件夹
    getFolderList ({ commit }, { projectId, repoName, roadMap, fullPath = '', isPipeline = false }) {
        let request
        if (isPipeline && !fullPath) {
            request = Vue.prototype.$ajax.get(
                `${prefix}/pipeline/list/${projectId}`
            ).then(records => ({ records }))
        } else {
            request = Vue.prototype.$ajax.post(
                `${prefix}/node/search`,
                {
                    page: {
                        pageNumber: 1,
                        pageSize: 10000
                    },
                    sort: {
                        properties: [isPipeline ? 'lastModifiedDate' : 'name'],
                        direction: isPipeline ? 'DESC' : 'ASC'
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
            )
        }
        return request.then(({ records }) => {
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
    // 仓库内自定义查询
    getArtifactoryList (_, { projectId, repoName, name, fullPath, current, limit, isPipeline = false, sortType = 'lastModifiedDate' }) {
        if (isPipeline && !fullPath && !name) {
            return Vue.prototype.$ajax.get(
                `${prefix}/pipeline/list/${projectId}`
            ).then(records => ({ records, totalRecords: 0 }))
        } else {
            return Vue.prototype.$ajax.post(
                `${prefix}/node/search`,
                {
                    page: {
                        pageNumber: current,
                        pageSize: limit
                    },
                    sort: {
                        properties: ['folder', sortType],
                        direction: 'DESC'
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
                            ...(name
                                ? [
                                    {
                                        field: 'name',
                                        value: `\*${name}\*`,
                                        operation: 'MATCH'
                                    }
                                ]
                                : [
                                    {
                                        field: 'path',
                                        value: `${fullPath === '/' ? '' : fullPath}/`,
                                        operation: 'EQ'
                                    }
                                ])
                        ],
                        relation: 'AND'
                    }
                }
            )
        }
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
            xhr.open('PUT', `/web/generic/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`, true)
            xhr.responseType = 'json'
            xhr.setRequestHeader('Content-Type', headers['Content-Type'])
            xhr.setRequestHeader('X-BKREPO-OVERWRITE', headers['X-BKREPO-OVERWRITE'])
            xhr.setRequestHeader('X-BKREPO-EXPIRES', headers['X-BKREPO-EXPIRES'])
            xhr.addEventListener('error', e => reject(e.target.response))
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
    shareArtifactory (_, body) {
        return Vue.prototype.$ajax.post(
            '/generic/temporary/url/create',
            body
        )
    },
    // 发送邮件
    sendEmail (_, body) {
        return Vue.prototype.$ajax.post(
            '/generic/notify/mail',
            body
        )
    },
    // 统计节点大小信息
    getFolderSize (_, { projectId, repoName, fullPath }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/node/size/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    },
    // 添加元数据
    addMetadata (_, { projectId, repoName, fullPath, body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/metadata/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            body
        )
    },
    // 删除元数据
    deleteMetadata (_, { projectId, repoName, fullPath, body }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/metadata/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            { data: body }
        )
    }
}
