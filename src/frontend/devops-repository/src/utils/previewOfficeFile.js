import axios from 'axios'
// 后端返回的类型为 application/octet-stream;charset=UTF-8
export function customizePreviewOfficeFile (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return axios({
        url: `${location.origin}/web/generic/` + url,
        method: 'get',
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function getPreviewLocalOfficeFileInfo (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return axios({
        url: `${location.origin}/web/preview/api/file/getPreviewInfo/` + url,
        method: 'get',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function getPreviewRemoteOfficeFileInfo(extraParam) {
    return axios({
        url: `${location.origin}/web/preview/api/file/getPreviewInfo/`,
        method: 'get',
        params: {
            extraParam: extraParam
        },
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function customizePreviewLocalOfficeFile (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return axios({
        url: `${location.origin}/web/preview/api/file/onlinePreview/` + url,
        method: 'get',
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function customizePreviewRemoteOfficeFile (extraParam) {
    return axios({
        url: `${location.origin}/web/preview/api/file/onlinePreview/`,
        method: 'get',
        params: {
            extraParam: extraParam
        },
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}
