import Vue from 'vue'
const prefix = 'docker/api'

export default {
    // docker镜像列表
    getDockerList (_, { projectId, repoName, dockerName, current = 1, limit = 10 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/repo/${projectId}/${repoName}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    name: dockerName
                }
            }
        )
    },
    // delete docker
    deleteDocker (_, { projectId, repoName, dockerName }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/repo/${projectId}/${repoName}/${dockerName}`
        )
    },
    // delete docker
    deleteDockerTag (_, { projectId, repoName, dockerName, tagName }) {
        return Vue.prototype.$ajax.delete(
            `${prefix}/tag/${projectId}/${repoName}/${dockerName}/${tagName}`
        )
    },
    // 获取repo的所有tag
    getDockerTagList (_, { projectId, repoName, dockerName, tagName, current = 1, limit = 1000 }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/tag/${projectId}/${repoName}/${dockerName}`,
            {
                params: {
                    pageNumber: current,
                    pageSize: limit,
                    tag: tagName
                }
            }
        )
    },
    // get docker tag detail
    getDockerTagDetail (_, { projectId, repoName, dockerName, tagName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/tagdetail/${projectId}/${repoName}/${dockerName}/${tagName}`
        )
    },
    // 获取manifest文件
    getManifest (_, { projectId, repoName, dockerName, tagName }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/manifest/${projectId}/${repoName}/${dockerName}/${tagName}`
        )
    },
    // 根据layerId下载layer文件
    downloadLayer (_, { projectId, repoName, dockerName, layerId }) {
        return Vue.prototype.$ajax.get(
            `${prefix}/layer/${projectId}/${repoName}/${dockerName}/${layerId}`
        )
    }
}
