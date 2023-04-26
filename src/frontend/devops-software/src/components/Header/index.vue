<template>
    <div class="bkrepo-header flex-align-center">
        <div class="flex-align-center">
            <router-link class="flex-align-center bkrepo-logo" :to="{ name: 'repoList' }">
                <svg
                    :width="34"
                    :height="34"
                    style="fill: currentColor"
                >
                    <use xlink:href="#repository" />
                </svg>
                <header class="ml10 bkrepo-title">{{ title }}</header>
            </router-link>
            <a class="ml20 link" target="_self" href="/ui">
                <i class="devops-icon icon-sort"></i>
                <span class="ml5">{{$t('RepoManage')}}</span>
            </a>
        </div>
        <div @click="changeLanguage" class="language-select">
            <img width="20" height="20" class="language-image" src="/ui/locale.svg" />
            <span>{{ language === 'zh-cn' ? 'English' : '中文' }}</span>
        </div>
        <User />
    </div>
</template>
<script>
    import User from '@repository/components/User'
    import { mapState, mapActions } from 'vuex'
    import cookies from 'js-cookie'
    export default {
        name: 'bkrepoHeader',
        components: { User },
        data () {
            return {
                language: ''
            }
        },
        computed: {
            ...mapState(['projectList']),
            projectId () {
                return this.$route.params.projectId
            },
            title () {
                return document.title
            }
        },
        created () {
            this.language = cookies.get('blueking_language') || 'zh-cn'
        },
        methods: {
            ...mapActions(['checkPM']),
            changeProject (projectId) {
                localStorage.setItem('projectId', projectId)
                if (this.projectId === projectId) return
                this.checkPM({ projectId })
                this.$router.push({
                    name: 'repoList',
                    params: {
                        projectId
                    },
                    query: this.$route.query
                })
            },
            changeLanguage () {
                const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')
                if (this.language === 'zh-cn') {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'en', { domain: BK_CI_DOMAIN, path: '/' })
                    location.reload()
                } else {
                    cookies.remove('blueking_language', { domain: BK_CI_DOMAIN, path: '/' })
                    cookies.set('blueking_language', 'zh-cn', { domain: BK_CI_DOMAIN, path: '/' })
                    location.reload()
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-header {
    height: 50px;
    padding: 0 20px;
    justify-content: space-between;
    background-color:  var(--fontPrimaryColor);
    color: white;
    .bkrepo-logo {
        color: white;
    }
    .bkrepo-title {
        font-size: 18px;
        letter-spacing: 1px;
    }
    .link {
        padding: 0 10px;
        color: white;
        line-height: 24px;
        border: 1px solid #FFFFFF33;
        background-color: #FFFFFF1A;
        .icon-sort {
            display:inline-block;
            font-size: 12px;
            transform: rotate(90deg);
        }
        &:hover {
            background-color: rgba(255, 255, 255, 0.4);
        }
    }
    .language-select:hover{
        cursor: pointer;
    }
    .language-select {
        margin-left: auto
    }
    .language-image{
        background-color: currentColor;
        float: left;
        margin-right: 3px
    }
}
</style>
