<template>
    <div class="bkrepo-main flex-column">
        <Header v-if="!iframeMode" />
        <router-view class="bkrepo-main-container"></router-view>
        <ConfirmDialog />
        <Login v-if="!iframeMode" />
    </div>
</template>

<script>
    import Header from '@/components/Header'
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

            if (this.iframeMode) {
                const url = /^http(s)/.test(DEVOPS_SITE_URL)
                    ? DEVOPS_SITE_URL + '/console/static/devops-utils.js'
                    : '/ui/devops-utils.js'
                this.loadDevopsUtils(url)
            } else {
                this.ajaxUserInfo()
                this.getRepoUserList()
                this.getProjectList()
            }
        },
        methods: {
            ...mapActions(['getProjectList', 'ajaxUserInfo', 'getRepoUserList'])
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
