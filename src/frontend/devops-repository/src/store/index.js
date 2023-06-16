import Vue from 'vue'
import Vuex from 'vuex'
import actions from './actions'

Vue.use(Vuex)

const storeObject = {
    state: {
        showLoginDialog: Boolean(window.login),
        permission: {
            write: true,
            edit: true,
            delete: true
        },
        genericTree: [
            {
                name: '/',
                displayName: '/',
                fullPath: '',
                folder: true,
                children: [],
                roadMap: '0'
            }
        ],
        projectList: [],
        repoListAll: [],
        scannerSupportFileNameExt: [],
        scannerSupportPackageType: [],
        userList: {
            anonymous: {
                id: 'anonymous',
                name: '/'
            }
        },
        userInfo: {
            username: '',
            name: '',
            email: '',
            phone: '',
            admin: false,
            manage: false
        },
        domain: {
            docker: '',
            npm: '',
            helm: ''
        },
        clusterList: []
    },
    getters: {
        masterNode (state) {
            return state.clusterList.find(v => v.type === 'CENTER') || { name: '', url: '' }
        },
        isEnterprise () {
            // 独立部署目前不区分版本
            return true
        }
    },
    mutations: {
        INIT_TREE (state, root) {
            state.genericTree = root
        },
        UPDATE_TREE (state, { roadMap, list }) {
            let tree = state.genericTree
            roadMap.split(',').forEach(index => {
                // 在移动或复制操作中选择的是最后一层元素时，可能是undefined，此时自然不应该执行后续操作
                if (tree[index]) {
                    if (!tree[index]?.children) Vue.set(tree[index], 'children', [])
                    tree = tree[index].children
                }
            })
            list = list.map(item => {
                const children = (tree.find(oldItem => oldItem.fullPath === item.fullPath) || {}).children || []
                return {
                    ...item,
                    children,
                    displayName: item.metadata?.displayName || item.name
                }
            })
            tree.splice(0, tree.length, ...list)
        },
        SET_USER_LIST (state, data) {
            state.userList = {
                ...data,
                anonymous: {
                    id: 'anonymous',
                    name: '/'
                }
            }
        },
        SET_USER_INFO (state, data) {
            state.userInfo = {
                ...state.userInfo,
                ...data
            }
        },
        SET_DOMAIN (state, { type, domain }) {
            state.domain = {
                ...state.domain,
                [type]: domain
            }
        },
        SET_CLUSTER_LIST (state, data) {
            state.clusterList = data
        },
        SET_PROJECT_LIST (state, data) {
            state.projectList = data.map(v => {
                return {
                    ...v,
                    id: v.name || v.englishName,
                    name: v.displayName || v.projectName
                }
            })
        },
        SET_REPO_LIST_ALL (state, data) {
            state.repoListAll = data
        },
        SHOW_LOGIN_DIALOG (state, show = true) {
            state.showLoginDialog = show
        },
        SET_SCANNER_SUPPORT_FILE_NAME_EXT_LIST (state, data) {
            state.scannerSupportFileNameExt = data
        },
        SET_SCANNER_SUPPORT_PACKAGE_TYPE_LIST (state, data) {
            state.scannerSupportPackageType = data
        }
    },
    actions
}

export function createExtStore ({ state = {}, getters = {}, mutations = {}, actions = {} }) {
    return new Vuex.Store({
        state: {
            ...storeObject.state,
            ...state
        },
        getters: {
            ...storeObject.getters,
            ...getters
        },
        mutations: {
            ...storeObject.mutations,
            ...mutations
        },
        actions: {
            ...storeObject.actions,
            ...actions
        }
    })
}

export default new Vuex.Store(storeObject)
