import Vue from 'vue'

const prefix = 'generic/api/user/share'

export default {
    getShareInfo (_, { projectId, shareId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/info`,
            {
                params: {
                    projectId,
                    id: shareId
                }
            }
        )
    },
    getShareNodeInfo (_, { projectId, repoName, path }) {
        return Vue.prototype.$ajax.get(
            `/repository/api/node/page/${projectId}/${repoName}/${path}`,
            {
                params: {
                    pageNumber: 1,
                    pageSize: 1
                }
            }
        )
    },
    getShareConfig (_, { projectId, repoName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/config`,
            {
                params: {
                    projectId,
                    repoName
                }
            }
        )
    },
    getShareDownloadUrl (_, { body }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/url`,
            body
        )
    },
    createApproval (_, { shareId }) {
        return Vue.prototype.$ajax.post(
            `${prefix}/approval/create/${shareId}`
        )
    },
    getApprovalStatus (_, { shareId, userId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/approval/status`,
            {
                params: {
                    shareId,
                    userId
                }
            }
        )
    }
}
