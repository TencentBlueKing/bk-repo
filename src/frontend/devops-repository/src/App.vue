<template>
    <div class="bkrepo-main flex-column">
        <Header v-if="!iframeMode" />
        <router-view class="bkrepo-main-container"></router-view>
        <ConfirmDialog />
        <Login v-if="!iframeMode" />
    </div>
</template>

<script>
    import Header from '@repository/components/Header'
    import Login from '@repository/components/Login'
    import { mapActions } from 'vuex'
    import cookies from 'js-cookie'
    import mixin from '@repository/AppMixin'
    export default {
        components: { Header, Login },
        mixins: [mixin],
        data () {
            return {
                iframeMode: MODE_CONFIG === 'ci'
            }
        },
        created () {
            const username = cookies.get('bk_uid')
            username && this.SET_USER_INFO({ username })

            const urlProjectId = (location.pathname.match(/^\/[a-zA-Z0-9]+\/([^/]+)/) || [])[1]
            const localProjectId = localStorage.getItem('projectId')
            if (this.iframeMode) {
                this.loadDevopsUtils('/ui/devops-utils.js')
                
                localStorage.setItem('projectId', urlProjectId || localProjectId || '')
                !urlProjectId && this.$router.replace({
                    name: 'repoList',
                    params: {
                        projectId: urlProjectId || localProjectId || ''
                    }
                })
            } else {
                Promise.all([this.ajaxUserInfo(), this.getProjectList(), this.getRepoUserList()]).then(() => {
                    let projectId = ''
                    if (!(urlProjectId && this.projectList.find(v => v.id === urlProjectId))) {
                        if (this.projectList.find(v => v.id === localProjectId)) {
                            projectId = localProjectId
                        } else {
                            projectId = (this.projectList[0] || {}).id
                        }
                        this.$router.replace({
                            name: 'repoList',
                            params: {
                                projectId
                            }
                        })
                    }
                    this.checkPM({ projectId: (projectId || urlProjectId || localProjectId) })
                })
            }
        },
        methods: {
            ...mapActions(['getProjectList', 'ajaxUserInfo', 'checkPM', 'getRepoUserList'])
        }
    }
</script>
<style lang="scss">
@import '@repository/scss/index';
.bkrepo-main {
    height: 100%;
    background-color: var(--bgWeightColor);
    .bkrepo-main-container {
        flex: 1;
        overflow: hidden;
    }
}
</style>
