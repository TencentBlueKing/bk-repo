import './webpack_public_path'

// 第三方库
import Vue from 'vue'
import axios from 'axios'
import cookies from 'js-cookie'
import BkUserDisplayName from '@blueking/bk-user-display-name'
import VueCompositionAPI from '@vue/composition-api'

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
 * 配置 Vue 2 + Composition API
 * 让 @vue-office/excel 等通过 vue-demi 调用 ref/reactive/computed/defineComponent 等 API 时能正常工作。
 * 注：vue-demi 在 v2 模式下会重新导出 @vue/composition-api 的 API，
 *     所以只要 Vue.use(VueCompositionAPI) 成功安装，下游组件即可拿到完整 Composition API。
 *     vue-demi 的 ESM/CJS 互操作问题通过 webpack resolve.alias 在打包层解决（见 webpack.base.js）。
 */
Vue.use(VueCompositionAPI)

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
        // 用户信息查询走 opdata 微服务，避免 auth 不可用导致页面初始化失败
        const { data } = await axios.get(window.BK_SUBPATH + 'web/opdata/api/user/info')
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
