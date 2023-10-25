import Vue from 'vue'
import cookie from 'js-cookie'

import repoGeneric from './repoGeneric'
import repoCommon from './repoCommon'
import token from './token'
import permission from './permission'
import nodeManage from './nodeManage'
import project from './project'
import scan from './scan'
import oauth from './oauth'

const prefix = 'repository/api'

export default {
    ...repoGeneric,
    ...repoCommon,
    ...token,
    ...permission,
    ...nodeManage,
    ...project,
    ...scan,
    ...oauth,
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
    createRepo({dispatch}, {body}) {
        return Vue.prototype.$ajax.post(
            `${prefix}/repo/create`,
            body
        )
    },
    // 校验仓库名称
    checkRepoName(_, {projectId, name}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/exist/${projectId}/${name}`
        )
    },
    // 分页查询仓库列表
    getRepoList(_, {projectId, current, limit, name, type}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/page/${projectId}/${current}/${limit}`,
            {
                params: {
                    name: name || undefined,
                    type: type || undefined
                }
            }
        ).then(res => ({
            ...res,
            records: MODE_CONFIG === 'ci'
                ? res.records.filter(v => v.name !== 'report' && v.name !== 'log')
                : res.records
        })) // 前端隐藏report仓库/log仓库
    },
    // 查询所有仓库
    getRepoListWithoutPage(_, {projectId, name, type}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/list/${projectId}`,
            {
                params: {
                    name: name || undefined,
                    type: type || undefined
                }
            }
        ).then(res => ({
            ...res,
            records: MODE_CONFIG === 'ci'
                ? res.filter(v => v.name !== 'report' && v.name !== 'log' && v.type !== 'RDS')
                : res.filter(v => v.type !== 'RDS')
        }))
    },
    // 查询仓库列表
    getRepoListAll({commit}, {projectId}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/list/${projectId}`
        ).then(res => {
            // 前端隐藏report仓库/log仓库
            commit('SET_REPO_LIST_ALL', res.filter(v => v.name !== 'report' && v.name !== 'log' && v.type !== 'RDS'))
        })
    },
    // 查询仓库信息
    getRepoInfo(_, {projectId, repoName, repoType}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/info/${projectId}/${repoName}/${repoType.toUpperCase()}`
        )
    },
    // 更新仓库信息
    updateRepoInfo(_, {projectId, name, body}) {
        return Vue.prototype.$ajax.post(
            `${prefix}/repo/update/${projectId}/${name}`,
            body
        )
    },
    // 删除仓库
    deleteRepoList({dispatch}, {projectId, name, forced = false}) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/repo/delete/${projectId}/${name}?forced=${forced}`
        )
    },
    // 查询项目列表
    getProjectList({commit}) {
        return Vue.prototype.$ajax.get(
            `${prefix}/project/list`
        ).then(res => {
            commit('SET_PROJECT_LIST', res)
        })
    },
    logout({commit}) {
        if (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') {
            window.postMessage({
                action: 'toggleLoginDialog'
            }, '*')
            // eslint-disable-next-line no-undef
            if (window.ADD_FROM_LOGOUT === 'not') {
                location.href = window.getLoginUrl()
            } else {
                location.href = window.getLoginUrl() + '&is_from_logout=1'
            }
        } else {
            cookie.remove('bkrepo_ticket')
            commit('SHOW_LOGIN_DIALOG', true)
        }
    }
}
