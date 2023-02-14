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
                <header class="ml10 bkrepo-title">{{ $t('RepoManage') }}</header>
            </router-link>
            <!-- <a class="ml20 link" target="_self" href="/software/repoList">
                <i class="devops-icon icon-sort"></i>
                <span class="ml5">Artifact Hub</span>
            </a> -->
            <bk-select
                class="ml20 bkre-project-select"
                :value="projectId"
                searchable
                :clearable="false"
                :placeholder="$t('inputProject')"
                @change="changeProject"
                size="small"
                :enable-virtual-scroll="projectList && projectList.length > 3000"
                :list="projectList">
                <bk-option v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
            </bk-select>
        </div>
        <div style="flex: 1;text-align: end" @click="changeLanguage" class="language-select">
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
    .bkre-project-select {
        width: 300px;
        color: white;
        border-color: #FFFFFF33;
        background-color: #FFFFFF1A;
        &:hover {
            background-color: rgba(255, 255, 255, 0.4);
        }
    }
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
}
</style>
