import Vue from 'vue'

const prefix = 'helm'

export default {

    // 获取最近一次的同步记录
    getHelmLatestSyncRecord (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/ext/getLatestSyncStatus/${projectId}/${repoName}`
        )
    },

    // helm同步
    syncHelmRepo (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/ext/refresh/${projectId}/${repoName}`
        )
    }
}
