import Vue from 'vue'

import generic from './generic'
import docker from './docker'
import search from './search'
import npm from './npm'

const prefix = 'repository/api'

export default {
    ...generic,
    ...docker,
    ...npm,
    ...search,
    /*
        创建仓库
        body: {
            "projectId": "test",
            "name": "generic-local",
            "type": "GENERIC",
            "category": "COMPOSITE",
            "public": false,
            "description": "repo description",
            "configuration": null,
            "storageCredentialsKey": null
        }
    */
    createRepo (_, { body }) {
        return Vue.prototype.$ajax.post(`${prefix}/repo/create`, body)
    },
    // 校验仓库名称
    checkRepoName (_, { projectId, name }) {
        return Vue.prototype.$ajax.get(`${prefix}/repo/exist/${projectId}/${name}`)
    },
    // 分页查询仓库列表
    getRepoList (_, { projectId, current, limit, name, type }) {
        return Vue.prototype.$ajax.get(`${prefix}/repo/page/${projectId}/${current}/${limit}?name=${name}&type=${type}`)
    },
    // 查询仓库列表
    getRepoListAll (_, { projectId, name, type }) {
        // return Vue.prototype.$ajax.get(`${prefix}/repo/list/${projectId}?name=${name}&type=${type}`)
        return Vue.prototype.$ajax.get(`${prefix}/repo/list/${projectId}`)
    },
    // 查询仓库信息
    getRepoInfo (_, { projectId, name, type }) {
        return Vue.prototype.$ajax.get(`${prefix}/repo/info/${projectId}/${name}/${type}`)
    },
    // 更新仓库信息
    updateRepoInfo (_, { projectId, name, body }) {
        return Vue.prototype.$ajax.post(`${prefix}/repo/update/${projectId}/${name}`, body)
    },
    // 删除仓库
    deleteRepoList (_, { projectId, name, forced = false }) {
        return Vue.prototype.$ajax.delete(`${prefix}/repo/delete/${projectId}/${name}?forced=${forced}`)
    },
    // 查询公有源列表
    getPublicProxy (_, { type }) {
        return Vue.prototype.$ajax.post(`${prefix}/proxy-channel/list/public/${type}`)
    }
}
