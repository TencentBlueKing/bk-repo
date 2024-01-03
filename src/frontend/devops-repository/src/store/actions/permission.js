import Vue from 'vue'

const authPrefix = 'auth/api'

export default {
    // 查询用户信息
    ajaxUserInfo ({ dispatch }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/info`
        ).then(({ userId }) => {
            return dispatch('getUserInfo', { userId })
        })
    },
    getUserInfo ({ state, commit }, { userId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/userinfo/${userId}`
        ).then(res => {
            res && commit('SET_USER_INFO', {
                ...res,
                username: res.userId
            })
            return state.userInfo
        })
    },
    // 用户是否指定项目下管理员
    checkPM ({ commit }, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/admin/${projectId}`
        ).then(res => {
            commit('SET_USER_INFO', {
                ...res,
                projectId,
                manage: res
            })
        })
    },
    // 分页查询用户列表
    getUserList (_, { current, limit, user, admin }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/page/${current}/${limit}`,
            {
                params: {
                    user,
                    admin
                }
            }
        )
    },
    getUserRelatedList (_, { asstUser }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/group`,
            {
                params: {
                    asstUser
                }
            }
        )
    },
    // 查询所有用户
    getRepoUserList ({ commit }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/list`
        ).then(res => {
            const data = res.reduce((target, item) => {
                target[item.userId] = {
                    id: item.userId,
                    name: item.name
                }
                return target
            }, {})
            commit('SET_USER_LIST', data)
        })
    },
    // 项目下用户列表
    getProjectUserList (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/list/${projectId}`
        )
    },
    // 校验userId是否重复
    checkUserId (_, { userId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/repeat/${userId}`
        )
    },
    // 创建用户
    createUser (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/create`,
            body
        )
    },
    // 批量创建用户
    importUsers (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/batch`,
            body
        )
    },
    // 编辑用户
    editUser (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/user/update/info/${body.userId}`,
            body
        )
    },
    // 修改密码
    modifyPwd (_, { userId, formData }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/user/update/password/${userId}`,
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        )
    },
    // 重置密码
    resetPwd (_, userId) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/reset/${userId}`
        )
    },
    // 删除用户
    deleteUser (_, userId) {
        return Vue.prototype.$ajax.delete(
            `${authPrefix}/user/delete/${userId}`
        )
    },
    validateEntityUser (_, userId) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/validateEntityUser/${userId}`
        )
    },
    // 新建角色
    createRole (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/role/create`,
            body
        )
    },
    // 编辑角色
    editRole (_, { id, body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/role/${id}`,
            body
        )
    },
    // 删除角色
    deleteRole (_, { id }) {
        return Vue.prototype.$ajax.delete(
            `${authPrefix}/role/delete/${id}`
        )
    },
    // 查询所有角色
    getRoleList () {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/role/sys/list`
        )
    },
    // 查询项目下角色
    getProjectRoleList (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/role/sys/list/${projectId}`
        )
    },
    // 查询所有部门
    getRepoDepartmentList (_, { departmentId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/department/list`,
            {
                params: {
                    departmentId
                }
            }
        ).then(res => res.map(v => ({ ...v, has_children: true })))
    },
    // 查询仓库所有权限
    getPermissionDetail (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/permission/list/inrepo`,
            {
                params: {
                    projectId,
                    repoName
                }
            }
        )
    },
    // 获取项目权限配置
    getProjectPermission (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/permission/list/inproject`,
            {
                params: {
                    projectId
                }
            }
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
    },
    // 通过部门id查询部门详情
    getRepoDepartmentDetail (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/department/listByIds`,
            body
        )
    },
    // 审计日志
    getAuditList (_, { projectId, startTime, endTime, user, current, limit }) {
        return Vue.prototype.$ajax.get(
            'repository/api/log/page',
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    projectId,
                    startTime,
                    endTime,
                    operator: user
                }
            }
        )
    },
    getPermissionUrl (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/auth/bkiamv3/permission/url`,
            body
        )
    },
    refreshIamPermission (_, { projectId }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/auth/bkiamv3/project/refresh/${projectId}`
        )
    },
    // 判断蓝鲸权限是否开启
    getIamPermissionStatus () {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/auth/bkiamv3/status`
        )
    },
    // 创建项目用户
    createProjectUser (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/user/create/project`,
            body
        )
    },
    // 创建repo内权限
    createPermissionDeployInRepo (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${authPrefix}/permission/create`,
            body
        )
    },
    // 删除repo内权限
    deletePermission (_, { id }) {
        return Vue.prototype.$ajax.delete(
            `${authPrefix}/permission/delete/${id}`
        )
    },
    // 更新repo内权限
    UpdatePermissionConfigInRepo (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/permission/update/config`,
            body
        )
    },
    // 获取repo内配置的权限
    listPermissionDeployInRepo (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/permission/list`,
            {
                params: {
                    projectId: projectId,
                    repoName: repoName,
                    resourceType: 'NODE'
                }
            }
        )
    }
}
