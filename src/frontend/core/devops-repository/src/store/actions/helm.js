import Vue from 'vue'

const prefix = 'helm'

export default {

    // helm同步
    syncHelmRepo (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/${projectId}/${repoName}/refresh`
        )
    }
}
