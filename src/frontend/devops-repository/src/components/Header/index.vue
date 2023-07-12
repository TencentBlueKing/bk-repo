<template>
    <div class="bkrepo-header flex-align-center">
        <div class="flex-align-center">
            <router-link class="flex-align-center bkrepo-logo" :to="{ name: 'repositories' }">
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
        <div style="text-align: end" class="language-select">
            <bk-popover
                theme="light navigation-message"
                placement="bottom"
                :arrow="false"
                trigger="click"
                ref="popoverRef"
            >
                <div class="flag-box">
                    <Icon :name="curLang.icon" size="20" />
                </div>
                <template slot="content">
                    <li
                        v-for="(item, index) in icons"
                        :key="index"
                        :class="['bkci-dropdown-item', { active: curLang.id === item.id }]"
                        @click="changeLanguage(item.id)">
                        <Icon class="mr5" :name="item.icon" style="vertical-align: top;margin-bottom: 2px;" size="20" />
                        {{item.name}}
                    </li>
                </template>
            </bk-popover>
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
        props: {
            icons: {
                type: Array,
                default: () => [
                    {
                        icon: 'english',
                        name: 'English',
                        id: 'en'
                    },
                    {
                        icon: 'chinese',
                        name: '中文',
                        id: 'zh-cn'
                    }
                ]
            }
        },
        data () {
            return {
                language: '',
                curLang: {
                    id: '',
                    icon: ''
                }
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
            this.curLang = this.icons.find(item => item.id === this.language) || { id: 'zh-cn', icon: 'chinese' }
        },
        methods: {
            ...mapActions(['checkPM']),
            changeProject (projectId) {
                localStorage.setItem('projectId', projectId)
                if (this.projectId === projectId) return
                this.checkPM({ projectId })
                this.$router.push({
                    name: 'repositories',
                    params: {
                        projectId
                    },
                    query: this.$route.query
                })
            },
            changeLanguage (id) {
                const BK_CI_DOMAIN = location.host.split('.').slice(1).join('.')
                if (id !== 'zh-cn') {
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
    .language-select {
        margin-left: auto;
    }
}
.flag-box{
    margin-top: 7px;
}
.bkci-dropdown-item {
    display: flex;
    align-items: center;
    height: 32px;
    line-height: 33px;
    padding: 0 16px;
    color: #63656e;
    font-size: 12px;
    text-decoration: none;
    white-space: nowrap;
    background-color: #fff;
    cursor: pointer;
    &:hover {
        background-color: #f5f7fb;
    }
    &.disabled {
        color: #dcdee5;
        cursor: not-allowed;
    }
    &.active {
        background-color: #f5f7fb;
    }
}
</style>
