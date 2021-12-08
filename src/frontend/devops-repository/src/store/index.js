import Vue from 'vue'
import Vuex from 'vuex'
import actions from './actions'

Vue.use(Vuex)

const storeObject = {
    state: {
        showLoginDialog: Boolean(window.login),
        genericTree: [
            {
                name: '/',
                fullPath: '',
                folder: true,
                children: [],
                roadMap: '0'
            }
        ],
        projectList: [],
        repoListAll: [],
        userList: {
            anonymous: {
                id: 'anonymous',
                name: '--'
            }
        },
        userInfo: {
            username: '',
            name: '',
            email: '',
            phone: '',
            admin: true,
            manage: false
        },
        domain: {
            docker: '',
            npm: ''
        },
        clusterList: []
    },
    getters: {
        masterNode (state) {
            return state.clusterList.find(v => v.type === 'CENTER') || { name: '', url: '' }
        }
    },
    mutations: {
        INIT_TREE (state, root) {
            state.genericTree = root
        },
        UPDATE_TREE (state, { roadMap, list }) {
            let tree = state.genericTree
            roadMap.split(',').forEach(index => {
                if (!tree[index].children) Vue.set(tree[index], 'children', [])
                tree = tree[index].children
            })
            list = list.map(item => {
                const children = (tree.find(oldItem => oldItem.fullPath === item.fullPath) || {}).children || []
                return {
                    ...item,
                    children,
                    name: (item.metadata && item.metadata.displayName) || item.name
                }
            })
            tree.splice(0, tree.length, ...list)
        },
        SET_USER_LIST (state, data) {
            state.userList = {
                ...data,
                anonymous: {
                    id: 'anonymous',
                    name: '--'
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
