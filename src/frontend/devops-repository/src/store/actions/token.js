import Vue from 'vue'

const prefix = 'auth/api'

export default {
    // 用户token列表
    getTokenList (_, { username }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/user/list/token/${username}`
        )
    },
    // 新增用户token
    addToken (_, { projectId, username, name, expiredAt = '' }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/user/token/${username}/${name}`,
            null,
            {
                params: {
                    projectId,
                    expiredAt
                }
            }
        )
    },
    // 删除用户token
    deleteToken (_, { username, name }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/user/token/${username}/${name}`
        )
    },
    // 查询用户信息
    ajaxUserInfo ({ commit }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/user/info`
        ).then(res => {
            res && commit('SET_USER_INFO', {
                username: res.userId
            })
        })
    },
    // 登录
    bkrepoLogin (_, formData) {
        return Vue.prototype.$ajax.post(
            `${prefix}/user/login`,
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        )
    }
}
