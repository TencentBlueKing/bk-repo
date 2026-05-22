import './webpack_public_path'

// 第三方库
import Vue from 'vue'
import axios from 'axios'
import cookies from 'js-cookie'
import BkUserDisplayName from '@blueking/bk-user-display-name'
import * as VueDemi from 'vue-demi'
import VueCompositionAPI, * as CompositionAPI from '@vue/composition-api'

// 本地模块
import App from '@/App'
import createRouter from '@/router'
import store from '@/store'
import '@repository/utils/request'

// 组件
import CanwayDialog from '@repository/components/CanwayDialog'
import EmptyData from '@repository/components/EmptyData'
import Icon from '@repository/components/Icon'

// 工具函数
import { throttleMessage, i18n, setLocale } from '@repository/utils'

// 常量配置
const THROTTLE_MESSAGE_DELAY = 3500
const DISPLAY_NAME_CACHE_DURATION = 1000 * 60 * 5 // 5分钟
const DEFAULT_LANGUAGE = 'zh-cn'

/**
 * 配置 vue-demi 以支持 Vue 2
 * 注意：@vue-office/excel 等组件内部通过 vue-demi 调用 ref/reactive/computed 等多个 Composition API，
 * 仅代理 defineComponent 会导致 setup 阶段抛 TypeError: e.ref is not a function（xlsx 预览白屏）。
 * 这里把整个 @vue/composition-api 命名空间合并到 VueDemi 上，并显式声明 Vue2 标记。
 */
Vue.use(VueCompositionAPI)
Object.assign(VueDemi, CompositionAPI, {
    Vue,
    Vue2: Vue,
    isVue2: true,
    isVue3: false,
    install: () => {} // 防止后续 Vue.use(VueDemi) 触发的 install 把上面合并的 API 覆盖
})

Vue.component('Icon', Icon)
Vue.component('CanwayDialog', CanwayDialog)
Vue.component('EmptyData', EmptyData)

// 全局属性配置
Vue.prototype.$setLocale = setLocale
Vue.prototype.$bkMessage = throttleMessage(Vue.prototype.$bkMessage, THROTTLE_MESSAGE_DELAY)
Vue.prototype.currentLanguage = cookies.get('blueking_language') || DEFAULT_LANGUAGE

document.title = i18n.t('webTitle')

async function setDisplayNamePlugin () {
    if (BK_REPO_ENABLE_MULTI_TENANT_MODE !== 'true') return
    try {
        const { data } = await axios.get(window.BK_SUBPATH + 'web/auth/api/user/info')
        BkUserDisplayName.configure({
            tenantId: data.data.tenantId,
            apiBaseUrl: API_BASE_URL,
            cacheDuration: DISPLAY_NAME_CACHE_DURATION,
            emptyText: '--'
        })
    } catch (error) {
        console.error('初始化displayName组件失败:', error)
    }
}

setDisplayNamePlugin()

Vue.mixin({
    methods: {
        // 特殊仓库名称替换
        replaceRepoName (name) {
            if (MODE_CONFIG === 'ci') {
                switch (name) {
                    case 'custom':
                        return this.$t('custom')
                    case 'pipeline':
                        return this.$t('pipeline')
                    default:
                        return name
                }
            }
            return name
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
    template: '<App />'
})

if (document.querySelector('#app')) window.repositoryVue.$mount('#app')
