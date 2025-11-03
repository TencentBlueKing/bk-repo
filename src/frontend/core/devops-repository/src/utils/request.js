import { routeBase } from '@repository/utils'
import axios from 'axios'
import Vue from 'vue'

const request = axios.create({
    baseURL: `${location.origin}/web`,
    validateStatus: status => {
        if (status > 400) {
            console.warn(`HTTP 请求出错 status: ${status}`)
        }
        return status >= 200 && status <= 503
    },
    withCredentials: true,
    xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
    xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
})

request.interceptors.request.use(config => {
    const param = window.repositoryVue.$router.currentRoute.params?.projectId
    if (param) {
        config.headers['X-BKREPO-PROJECT-ID'] = param
    }
    config.headers['X-BKREPO-TIME-ZONE'] = new Date().getTimezoneOffset()
    return config
})

function errorHandler (error) {
    console.log('error catch', error)
    return Promise.reject(Error('网络出现问题，请检查你的网络是否正常'))
}

request.interceptors.response.use(response => {
    const { data: { data, message }, status } = response
    if (status === 200 || status === 206) {
        return data === undefined ? response.data : data
    } else if (status === 401 || status === 402) {
        if (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') {
            window.postMessage({
                action: 'toggleLoginDialog'
            }, '*')
            location.href = window.getLoginUrl()
        } else {
            window.repositoryVue.$store.commit('SHOW_LOGIN_DIALOG')
        }
    } else if (status === 440) {
        const projectId = localStorage.getItem('projectId')
        const path = routeBase + projectId + '/440/' + message
        window.repositoryVue.$router.push({ path: path, replace: true })
    }
    return Promise.reject({ status, message }) // eslint-disable-line
}, errorHandler)

Vue.prototype.$ajax = request

export default request
