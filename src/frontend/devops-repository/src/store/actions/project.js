import Vue from 'vue'

const prefix = 'repository/api'

export default {
    // 创建项目
    createProject (_, { id, name, description }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/project/create`,
            {
                name: id,
                displayName: name,
                description
            }
        )
    },
    // 编辑项目
    editProject (_, { id, name, description }) {
        return Vue.prototype.$ajax.put(
            `${prefix}/project/${id}`,
            {
                displayName: name,
                description
            }
        )
    },
    // 校验项目信息
    checkProject (_, { id, name }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/project/exist`,
            {
                params: {
                    name: id,
                    displayName: name
                }
            }
        )
    },
    // 查询项目列表
    queryProjectList (_, { sortProperty, direction }) {
        let url = `${prefix}/project/list`
        if (sortProperty !== '') {
            url = url + '?sortProperty=' + sortProperty + '&direction=' + direction
        }
        return Vue.prototype.$ajax.get(url)
    },
    // 分页查询项目列表
    queryProjectListByPage (_, { keyword, page = 1, size = 20 }) {
        const url = `${prefix}/project/list`
        return Vue.prototype.$ajax.get(url, {
            params: {
                displayNameMatch: keyword,
                pageNumber: page,
                pageSize: size
            }
        })
    }
}
