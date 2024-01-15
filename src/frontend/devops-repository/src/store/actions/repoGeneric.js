import Vue from 'vue'
import cookies from 'js-cookie'

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
    // 批量查询所有文件数量（递归）
    getMultiFileNumOfFolder (_, { projectId, repoName, paths }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/batch/${projectId}/${repoName}`,
            paths
        )
    },
    // 批量查询所有文件夹数量（递归）
    getMultiFolderNumOfFolder (_, { projectId, repoName, paths, isFolder }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/node/batch/${projectId}/${repoName}`,
            paths,
            {
                params: {
                    isFolder: isFolder
                }
            }
        )
    },
    // 请求文件夹下的子文件夹
    getFolderList ({ commit }, { projectId, repoName, roadMap, fullPath = '', isPipeline = false, localRepo = true }) {
        let request
        if (isPipeline && !fullPath && localRepo) {
            request = Vue.prototype.$ajax.get(
                `${prefix}/pipeline/list/${projectId}`
            ).then(records => ({ records }))
        } else {
            request = Vue.prototype.$ajax.post(
                localRepo ? `${prefix}/node/search` : `generic/${projectId}/${repoName}/search`,
                {
                    select: ['name', 'fullPath', 'metadata'],
                    page: {
                        pageNumber: 1,
                        pageSize: 10000
                    },
                    sort: {
                        properties: ['lastModifiedDate'],
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
    getNodeDetail (_, { projectId, repoName, fullPath = '', localNode = true }) {
        return Vue.prototype.$ajax.get(
            localNode
                ? `${prefix}/node/detail/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
                : `generic/detail/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`
        )
    },
    // 仓库内自定义查询
    getArtifactoryList (_, { projectId, repoName, name, fullPath, current, limit, isPipeline = false, sortType, searchFlag, localRepo = true }) {
        if (isPipeline && !fullPath && !name && localRepo) {
            return Vue.prototype.$ajax.get(
                `${prefix}/pipeline/list/${projectId}`
            ).then(records => ({ records, totalRecords: 0 }))
        } else {
            return Vue.prototype.$ajax.post(
                localRepo ? `${prefix}/node/search` : `generic/${projectId}/${repoName}/search`,
                {
                    page: {
                        pageNumber: current,
                        pageSize: limit
                    },
                    sort: sortType,
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
                                operation: searchFlag ? 'PREFIX' : 'EQ'
                            },
                            ...(name
                                ? [
                                    {
                                        field: 'name',
                                        value: `*${name}*`,
                                        operation: 'MATCH'
                                    }
                                ]
                                : [])
                        ],
                        relation: 'AND'
                    }
                }
            )
        }
    },
    // 查询元数据标签列表
    getMetadataLabelList (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/metadata/label/${projectId}`
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
                        reject(xhr)
                    }
                }
            }
            xhr.upload.addEventListener('progress', progressHandler)
            xhr.open('PUT', `/web/generic/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`, true)
            xhr.responseType = 'json'
            xhr.setRequestHeader('Content-Type', headers['Content-Type'])
            xhr.setRequestHeader('X-BKREPO-OVERWRITE', headers['X-BKREPO-OVERWRITE'])
            xhr.setRequestHeader('X-BKREPO-EXPIRES', headers['X-BKREPO-EXPIRES'])
            xhr.setRequestHeader('X-CSRFToken', cookies.get((MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket'))
            xhr.setRequestHeader('Accept-Language', cookies.get('blueking_language') || 'zh-CN')
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
    // 批量删除文件
    deleteMultiArtifactory (_, { projectId, repoName, paths }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/node/batch/${projectId}/${repoName}`,
            {
                data: paths
            }
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
            '/generic/notify/mail/user',
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
    // generic禁止使用/解除禁止
    forbidMetadata (_, { projectId, repoName, fullPath, body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/metadata/forbid/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            body
        )
    },
    // package禁止使用/解除禁止
    forbidPackageMetadata (_, { projectId, repoName, body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/metadata/package/forbid/${projectId}/${repoName}`,
            body
        )
    },
    // 删除元数据
    deleteMetadata (_, { projectId, repoName, fullPath, body }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/metadata/${projectId}/${repoName}/${encodeURIComponent(fullPath)}`,
            { data: body }
        )
    },
    // 预览基本文件
    previewBasicFile (_, { projectId, repoName, path }) {
        return Vue.prototype.$ajax.get(
            `generic/${projectId}/${repoName}${path}?preview=true`, {
                headers: {
                    range: 'bytes=0-52428800'
                }
            }
        )
    },
    previewCompressedFileList (_, { projectId, repoName, path }) {
        return Vue.prototype.$ajax.get(
            `generic/compressed/list/${projectId}/${repoName}${path}`
        )
    },
    previewCompressedBasicFile (_, { projectId, repoName, path, filePath }) {
        return Vue.prototype.$ajax.get(
            `generic/compressed/preview/${projectId}/${repoName}${path}?filePath=${filePath}`
        )
    },
    getProjectMetrics (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/project/metrics/${projectId}`
        )
    },
    // 清理创建时间早于{date}的文件节点
    cleanNode (_, { path, date }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/node/clean/${path}`,
            {
                params: {
                    date: date
                }
            }
        )
    },
    // 查询repo下一级目录
    getFirstLevelFolder (_, { projectId, repoName, fullPath = '', isPipeline = false, localRepo = true }) {
        let request
        if (isPipeline && !fullPath && localRepo) {
            request = Vue.prototype.$ajax.get(
                `${prefix}/pipeline/list/${projectId}`
            ).then(records => ({ records }))
        } else {
            request = Vue.prototype.$ajax.post(
                localRepo ? `${prefix}/node/search` : `generic/${projectId}/${repoName}/search`,
                {
                    select: ['name', 'fullPath', 'metadata'],
                    page: {
                        pageNumber: 1,
                        pageSize: 10000
                    },
                    sort: {
                        properties: ['lastModifiedDate'],
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
        return request
    }
}
