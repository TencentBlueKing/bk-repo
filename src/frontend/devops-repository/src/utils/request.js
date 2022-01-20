import axios from 'axios'
import Vue from 'vue'

const request = axios.create({
    baseURL: location.origin + '/web',
    validateStatus: status => {
        if (status > 400) {
            console.warn(`HTTP 请求出错 status: ${status}`)
        }
        return status >= 200 && status <= 503
    },
    withCredentials: true,
    xsrfCookieName: 'backend_csrftoken', // 注入csrfToken
    xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
})

function errorHandler (error) {
    console.log('error catch', error)
    return Promise.reject(Error('网络出现问题，请检查你的网络是否正常'))
}

request.interceptors.response.use(response => {
    const { data: { code, data, message }, status } = response
    if (status === 401 || status === 402) {
        if (MODE_CONFIG === 'ci') {
            window.postMessage({
                action: 'toggleLoginDialog'
            }, '*')
            location.href = window.getLoginUrl()
        } else {
            window.repositoryVue.$store.commit('SHOW_LOGIN_DIALOG')
        }
        return Promise.reject() // eslint-disable-line
    } else if (status === 403) {
        // window.repositoryVue.$router.replace({ name: 'repoList' })
        return Promise.reject({ // eslint-disable-line
            status,
            message: '未获得授权'
        })
    } else if (status === 500 || status === 503 || status === 512) {
        return Promise.reject({ // eslint-disable-line
            status,
            message: '服务维护中，请稍候...'
        })
    } else if (status === 400 || status === 404) {
        return Promise.reject({ // eslint-disable-line
            status,
            message
        })
    } else if (typeof code !== 'undefined' && code !== 0) {
        let msg = message
        if (Object.prototype.toString.call(message) === '[object Object]') {
            msg = Object.keys(message).map(key => message[key].join(';')).join(';')
        } else if (Object.prototype.toString.call(message) === '[object Array]') {
            msg = message.join(';')
        }
        return Promise.reject({ // eslint-disable-line
            status,
            message: msg
        })
    }

    return response.data instanceof Blob ? response.data : data
}, errorHandler)

Vue.prototype.$ajax = request

export default request
