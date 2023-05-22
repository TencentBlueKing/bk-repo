import Vue from 'vue'
import App from '@/App'
import createRouter from '@/router'
import store from '@/store'
import '@repository/utils/request'
import i18nLocal from '@repository/utils/index'

import Icon from '@repository/components/Icon'
import CanwayDialog from '@repository/components/CanwayDialog'
import EmptyData from '@repository/components/EmptyData'
import { throttleMessage } from '@repository/utils'

Vue.component('Icon', Icon)
Vue.component('CanwayDialog', CanwayDialog)
Vue.component('EmptyData', EmptyData)

Vue.prototype.$setLocale = i18nLocal.setLocale
Vue.prototype.$bkMessage = throttleMessage(Vue.prototype.$bkMessage, 3500)

const i18n = i18nLocal.i18n

document.title = i18n.t('webTitle')

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
    template: '<App/>'
})

if (document.querySelector('#app')) window.repositoryVue.$mount('#app')
