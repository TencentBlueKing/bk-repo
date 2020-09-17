import Vue from 'vue'
import Vuex from 'vuex'
import actions from './actions'

Vue.use(Vuex)

export default new Vuex.Store({
    state: {
        breadcrumb: [],
        genericTree: [
            {
                name: '/',
                fullPath: '',
                folder: true,
                children: [],
                roadMap: '0'
            }
        ]
    },
    getters: {
    },
    mutations: {
        INIT_GENERIC_TREE (state) {
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
        UPDATE_GENERIC_TREE (state, { roadMap, list }) {
            let tree = state.genericTree
            roadMap.split(',').forEach(index => {
                if (!tree[index].children) Vue.set(tree[index], 'children', [])
                tree = tree[index].children
            })
            list = list.map(item => {
                const children = (tree.find(oldItem => oldItem.fullPath === item.fullPath) || {}).children || []
                return { ...item, children }
            })
            tree.splice(0, tree.length, ...list)
        },
        SET_BREADCRUMB (state, data) {
            state.breadcrumb = data
        }
    },
    actions
})
