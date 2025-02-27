import Vue from 'vue'

const prefix = 'helm'

export default {

    // 同步配置与记录表的数据
    syncRecordWithConfig (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/ext/syncFreshRecord/${projectId}/${repoName}`
        )
    },

    // 获取最近一次的同步记录
    getHelmLatestSyncRecord (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/ext/getLatestSyncStatus/${projectId}/${repoName}`
        )
    },

    // helm同步
    syncHelmRepo (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/${projectId}/${repoName}/refresh`
        )
    }
}
