import Vue from 'vue'

const authPrefix = 'auth/api'

export default {
    // 查询所有用户
    getRepoUserList () {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/list`
        )
    },
    // 查询所有角色
    getRepoRoleList (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/role/list?projectId=${projectId}&repoName=${repoName}`
        )
    },
    // 查询所有部门
    getRepoDepartmentList () {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/department/list`
        )
    },
    // 查询仓库所有权限
    getPermissionDetail (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/permission/list/inrepo?projectId=${projectId}&repoName=${repoName}`
        )
    },
    // 更新权限绑定用户
    setUserPermission (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/permission/user`,
            body
        )
    },
    // 更新权限绑定角色
    setRolePermission (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/permission/role`,
            body
        )
    },
    // 更新权限绑定部门
    setDepartmentPermission (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/permission/department`,
            body
        )
    },
    // 更新权限绑定动作
    setActionPermission (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/permission/action`,
            body
        )
    }
}
