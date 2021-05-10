import Vue from 'vue'
import cookie from 'js-cookie'

import repoGeneric from './repoGeneric'
import repoCommon from './repoCommon'
import token from './token'
import permission from './permission'

const prefix = 'repository/api'

export default {
    ...repoGeneric,
    ...repoCommon,
    ...token,
    ...permission,
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
        return Vue.prototype.$ajax.post(
            `${prefix}/repo/create`,
            body
        )
    },
    // 校验仓库名称
    checkRepoName (_, { projectId, name }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/exist/${projectId}/${name}`
        )
    },
    // 分页查询仓库列表
    getRepoList (_, { projectId, current, limit, name, type }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/page/${projectId}/${current}/${limit}`,
            {
                params: {
                    name,
                    type
                }
            }
        )
    },
    // 查询仓库列表
    getRepoListAll (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/list/${projectId}`
        )
    },
    // 查询仓库信息
    getRepoInfo (_, { projectId, repoName, repoType }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/info/${projectId}/${repoName}/${repoType}`
        )
    },
    // 更新仓库信息
    updateRepoInfo (_, { projectId, name, body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/repo/update/${projectId}/${name}`,
            body
        )
    },
    // 删除仓库
    deleteRepoList (_, { projectId, name, forced = false }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/repo/delete/${projectId}/${name}?forced=${forced}`
        )
    },
    // 查询公有源列表
    getPublicProxy (_, { repoType }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/proxy-channel/list/public/${repoType}`
        )
    },
    // 查询项目列表
    getProjectList ({ commit }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/project/list`
        ).then(res => {
            commit('SET_PROJECT_LIST', res.map(v => {
                return {
                    id: v.name,
                    name: v.displayName
                }
            }))
        })
    },
    logout () {
        if (MODE_CONFIG === 'standalone') {
            cookie.remove('bkrepo_ticket')
            location.reload()
        } else {
            window.postMessage({
                action: 'toggleLoginDialog'
            }, '*')
            location.href = window.getLoginUrl()
        }
    }
}
