import Vue from 'vue'
import App from './App'
import createRouter from './router'
import store from './store'
import '@/utils/request'

import Icon from '@/components/Icon'
import createLocale from '../../locale'
import '@icon-cool/bk-icon-devops/src/index'
import { throttleMessage } from './utils'

import bkMagic from 'bk-magic-vue'
// 全量引入 bk-magic-vue 样式
require('bk-magic-vue/dist/bk-magic-vue.min.css')

const requireAll = requireContext => requireContext.keys().map(requireContext)
const req = require.context('@/images', false, /\.svg$/)
requireAll(req)

const { i18n, setLocale } = createLocale(require.context('@locale/repository/', false, /\.json$/))

Vue.component('Icon', Icon)

Vue.use(bkMagic)

Vue.prototype.$setLocale = setLocale
Vue.prototype.$bkMessage = throttleMessage(Vue.prototype.$bkMessage, 3500)

Vue.mixin({
    methods: {
        // 特殊仓库名称替换
        replaceRepoName (name) {
            switch (name) {
                case 'custom':
                    return '自定义仓库'
                case 'pipeline':
                    return '流水线仓库'
                default:
                    return name
            }
        }
    }
})

window.repositoryVue = new Vue({
    router: createRouter(store),
    i18n,
    store,
    components: {
        App
    },
    template: '<App/>'
})

if (document.querySelector('#app')) window.repositoryVue.$mount('#app')
