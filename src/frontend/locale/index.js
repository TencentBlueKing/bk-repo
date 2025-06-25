import VueI18n from 'vue-i18n'
import Vue from 'vue'
import axios from 'axios'
import cookies from 'js-cookie'
const { lang, locale } = window.bkMagicVue
const DEFAULT_LOCALE = 'zh-CN'
const LS_KEY = 'blueking_language'
const loadedModule = {}
const localeLabelMap = {
    'zh-CN': '中文',
    'zh-cn': '中文',
    cn: '中文',
    'en-US': 'English',
    'en-us': 'English',
    en: 'English',
    us: 'English'
}
const localeAliasMap = {
    'zh-cn': 'zh-CN',
    cn: 'zh-CN',
    'en-us': 'en-US',
    en: 'en-US',
    us: 'en-US',
    // 设置蓝鲸cookie使用
    'zh-CN': 'zh-cn',
    'en-US': 'en',
    'ja': 'ja'
}

const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')

function getLsLocale () {
    try {
        const cookieLcale = cookies.get(LS_KEY) || DEFAULT_LOCALE
        return localeAliasMap[cookieLcale.toLowerCase()] || DEFAULT_LOCALE
    } catch (error) {
        return DEFAULT_LOCALE
    }
}

function setLsLocale (locale) {
    if (typeof cookies.set === 'function') {
        cookies.remove(LS_KEY, { domain: BK_CI_DOMAIN, path: '/' })
        cookies.set(LS_KEY, localeAliasMap[locale], { domain: BK_CI_DOMAIN, path: '/', expires: 366 })
    }
}

export default (r) => {
    Vue.use(VueI18n)
    const { messages, localeList } = importAll(r)
    const initLocale = getLsLocale()
    // export localeList
    const i18n = new VueI18n({
        silentFallbackWarn: true,
        locale: initLocale,
        fallbackLocale: initLocale,
        messages
    })

    setLocale(initLocale)

    locale.i18n((key, value) => i18n.t(key, value))

    function dynamicLoadModule (module, locale = DEFAULT_LOCALE) {
        const localeModuleId = getLocalModuleId(module, locale)
        if (loadedModule[localeModuleId]) {
            return Promise.resolve()
        }
        return axios.get(`/${module}/${locale}.json?t=${+new Date()}`, {
            crossdomain: true
        }).then(response => {
            const messages = response.data

            i18n.setLocaleMessage(locale, {
                ...i18n.messages[locale],
                [module]: messages
            })
            loadedModule[localeModuleId] = true
        })
    }

    function setLocale (localeLang) {
        Object.keys(loadedModule).map(mod => {
            const [, module] = mod.split('_')
            if (!loadedModule[getLocalModuleId(module, localeLang)]) {
                dynamicLoadModule(module, localeLang)
            }
        })
        i18n.locale = localeLang
        setLsLocale(localeLang)
        locale.use(lang[localeLang.replace('-', '')])
        axios.defaults.headers.common['Accept-Language'] = localeLang
        if (Vue.prototype.$ajax?.defaults) {
            Vue.prototype.$ajax.defaults.headers.common['Accept-Language'] = localeLang
        }
        document.querySelector('html').setAttribute('lang', localeLang)

        return localeLang
    }

    return {
        i18n,
        setLocale,
        localeList,
        dynamicLoadModule
    }
}

function getLocalModuleId (module, locale) {
    return `${locale}_${module}`
}

function importAll (r) {
    const localeList = []
    const messages = r.keys().reduce((acc, key) => {
        const mod = r(key)

        const matchLocaleKey = key.match(/\/([\w-]+)?\.json$/)
        const localeKey = (matchLocaleKey ? matchLocaleKey[1] : '')
        if (localeKey) {
            acc[localeKey] = {
                ...lang[localeKey.replace('-', '')],
                ...mod
            }
            localeList.push({
                key: localeKey,
                label: localeLabelMap[localeKey]
            })
        }
        return acc
    }, {})

    return {
        localeList,
        messages
    }
}
