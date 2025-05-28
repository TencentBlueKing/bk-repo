import axios from 'axios'
// 后端返回的类型为 application/octet-stream;charset=UTF-8
export function customizeDownloadFile (projectId, repoName, fullPaths) {
    const vm = window.repositoryVue
    axios({
        url: `${location.origin}/web/generic/batch/${projectId}/${repoName}`,
        method: 'POST',
        data: { paths: fullPaths },
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        headers: { 'X-BKREPO-PROJECT-ID': vm.$router.currentRoute.params?.projectId },
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    }).then(res => {
        const blobUrl = window.URL.createObjectURL(res.data)
        // 从请求头中获取文件名（文件名在请求头的content-disposition属性的filename属性中。）
        // 如果在请求头中获取不到 content-disposition 内容，需要后端在response的header中设置： Access-Control-Expose-Headers: Content-Disposition
        if (res.headers['content-disposition']) {
            const disposition = res.headers['content-disposition']
            // 使用 lastIndexOf 并从  filename*=  之后在加17是因为  filename*= 占了十位字符，UTF-8''占七位字符
            let fileName = disposition.substring(
                disposition.lastIndexOf('filename*=') + 17,
                disposition.length
            )
            // 将iso8859-1的字符转化为中文字符
            fileName = decodeURI(escape(fileName))

            const link = document.createElement('a')
            link.href = blobUrl
            link.style.display = 'none'
            link.download = fileName
            document.body.appendChild(link)
            link.click()
            window.URL.revokeObjectURL(blobUrl)
            document.body.removeChild(link)
            vm.$bkNotify({
                title: vm.$t('batchDownloadingInfo'),
                position: 'bottom-right',
                theme: 'success'
            })
        } else {
            vm.$bkMessage({
                message: vm.$t('noFilenameErrorInfo'),
                theme: 'error'
            })
            console.log('获取不到文件名称')
        }
    }).catch((e) => {
        // 下载文件报错，此时后端返回的数据类型应该是 json
        if (e.response.data.type === 'application/json') {
            const reader = new FileReader()
            reader.readAsText(e.response.data, 'utf-8')
            reader.addEventListener('loadend', function () {
                const errorData = JSON.parse(reader.result)
                vm.$bkMessage({
                    message: errorData.message,
                    theme: 'error'
                })
            })
        } else {
            // 后端返回的数据类型不是 json
            vm.$bkMessage({
                message: vm.$t('batchDownloadBackTypeErrorInfo'),
                theme: 'error'
            })
        }
    })
}

export function downloadFile (url) {
    const vm = window.repositoryVue
    axios({
        url: `${location.origin}/web${url}`,
        method: 'GET',
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        headers: { 'X-BKREPO-PROJECT-ID': vm.$router.currentRoute.params?.projectId },
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    }).then(res => {
        const blobUrl = window.URL.createObjectURL(res.data)
        // 从请求头中获取文件名（文件名在请求头的content-disposition属性的filename属性中。）
        // 如果在请求头中获取不到 content-disposition 内容，需要后端在response的header中设置： Access-Control-Expose-Headers: Content-Disposition
        if (res.headers['content-disposition']) {
            const disposition = res.headers['content-disposition']
            // 使用 lastIndexOf 并从  filename*=  之后在加17是因为  filename*= 占了十位字符，UTF-8''占七位字符
            let fileName = disposition.substring(
                disposition.lastIndexOf('filename*=') + 17,
                disposition.length
            )
            // 将iso8859-1的字符转化为中文字符
            fileName = decodeURI(escape(fileName))
            const link = document.createElement('a')
            link.href = blobUrl
            link.style.display = 'none'
            link.download = fileName
            document.body.appendChild(link)
            link.click()
            window.URL.revokeObjectURL(blobUrl)
            document.body.removeChild(link)
        }
    })
}
