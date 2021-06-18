import Vue from 'vue'
import Vuex from 'vuex'
import actions from './actions'

Vue.use(Vuex)

export default new Vuex.Store({
    state: {
        showLoginDialog: false,
        breadcrumb: [],
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
        userList: {},
        userInfo: {
            username: '',
            name: '',
            email: '',
            phone: '',
            admin: true
        },
        dockerDomain: ''
    },
    getters: {
    },
    mutations: {
        INIT_TREE (state) {
            state.genericTree = [
                {
                    name: '/',
                    fullPath: '',
                    folder: true,
                    children: [],
                    roadMap: '0'
                }
            ]
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
        SET_BREADCRUMB (state, data) {
            state.breadcrumb = data
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
        SET_DOCKER_DOMAIN (state, data) {
            state.dockerDomain = data
        },
        SET_PROJECT_LIST (state, data) {
            state.projectList = data
        },
        SHOW_LOGIN_DIALOG (state, show = true) {
            state.showLoginDialog = show
        }
    },
    actions
})
