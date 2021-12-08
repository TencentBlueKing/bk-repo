import Vue from 'vue'

const authPrefix = 'auth/api'

export default {
    // 查询用户信息
    ajaxUserInfo ({ dispatch }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/info`
        ).then(({ userId }) => {
            dispatch('getUserInfo', { userId })
        })
    },
    getUserInfo ({ commit }, { userId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/user/userinfo/${userId}`
        ).then(res => {
            res && commit('SET_USER_INFO', {
                ...res,
                username: res.userId
            })
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
    // 编辑用户
    editUser (_, { body }) {
        return Vue.prototype.$ajax.put(
            `${authPrefix}/user/${body.userId}`,
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
    // 删除用户
    deleteUser (_, userId) {
        return Vue.prototype.$ajax.delete(
            `${authPrefix}/user/${userId}`
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
            `${authPrefix}/sys/role/list`
        )
    },
    // 查询项目下角色
    getProjectRoleList (_, { projectId }) {
        return Vue.prototype.$ajax.get(
            `${authPrefix}/sys/role/list/${projectId}`
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
        )
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
            'repository/api/operate/log/page',
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
    }
}
