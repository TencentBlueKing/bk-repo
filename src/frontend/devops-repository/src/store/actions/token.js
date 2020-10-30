import Vue from 'vue'

export default {
    // 用户token列表
    getTokenList (_, { username }) {
        return Vue.prototype.$ajax.get(
            `auth/api/user/list/token/${username}`
        )
    },
    // 新增用户token
    addToken (_, { projectId, username, name, expiredAt }) {
        return Vue.prototype.$ajax.post(
            `auth/api/user/token/${username}/${name}?projectId=${projectId}${expiredAt ? `&expiredAt=${expiredAt}` : ''}`
        )
    },
    // 删除用户token
    deleteToken (_, { username, name }) {
        return Vue.prototype.$ajax.delete(
            `auth/api/user/token/${username}/${name}`
        )
    }
}
