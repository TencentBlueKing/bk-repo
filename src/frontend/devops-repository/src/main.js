import Vue from 'vue'
import App from './App'
import createRouter from './router'
import store from './store'
import '@/utils/request'

import Icon from '@/components/Icon'
import VeeValidate from 'vee-validate'
import validationENMessages from 'vee-validate/dist/locale/en'
import validationCNMessages from 'vee-validate/dist/locale/zh_CN'
import ExtendsCustomRules from './utils/customRules'
import validDictionary from './utils/validDictionary'
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

Vue.use(focus)
Vue.use(bkMagic)

Vue.use(VeeValidate, {
    i18nRootKey: 'validations', // customize the root path for validation messages.
    i18n,
    fieldsBagName: 'veeFields',
    dictionary: {
        'en-US': validationENMessages,
        'zh-CN': validationCNMessages
    }
})
VeeValidate.Validator.localize(validDictionary)
ExtendsCustomRules(VeeValidate.Validator.extend)

Vue.prototype.$setLocale = setLocale
Vue.prototype.$bkMessage = throttleMessage(Vue.prototype.$bkMessage, 3500)

window.Vue = Vue

window.repositoryVue = new Vue({
    el: '#app',
    router: createRouter(store),
    i18n,
    store,
    components: {
        App
    },
    template: '<App/>'
})
