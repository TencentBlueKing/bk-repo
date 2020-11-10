<template>
    <div class="bkrepo-header flex-align-center">
        <div class="flex-align-center">
            <router-link class="flex-align-center bkrepo-logo" :to="{ name: 'repoList' }">
                <svg
                    :width="34"
                    :height="34"
                    style="fill: currentColor"
                >
                    <use xlink:href="#color-logo-bkrepo" />
                </svg>
                <header class="ml10 bkrepo-title">{{ $t('bkrepo') }}</header>
            </router-link>
            <bk-select
                class="bkre-project-select"
                :value="projectId"
                searchable
                :clearable="false"
                placeholder="请选择项目"
                @change="changeProject">
                <bk-option v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
            </bk-select>
        </div>
        <User />
    </div>
</template>
<script>
    import User from '../User'
    import { mapState } from 'vuex'
    export default {
        name: 'bkrepoHeader',
        components: { User },
        computed: {
            ...mapState(['projectList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        methods: {
            changeProject (projectId) {
                localStorage.setItem('projectId', projectId)
                if (this.projectId === projectId) return
                this.$router.push({
                    name: this.$route.name || 'repoList',
                    params: {
                        projectId
                    },
                    query: this.$route.query
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.bkrepo-header {
    height: 50px;
    padding: 0 20px;
    justify-content: space-between;
    background-color:  #191929;
    color: white;
    .bkre-project-select {
        border: 0 none;
        width: 200px;
        font-size: 14px;
        color: white;
        /deep/ .bk-select-angle {
            color: white;
        }
    }
    .bkrepo-logo {
        margin-right: 120px;
        color: white;
    }
    .bkrepo-title {
        font-size: 18px;
        letter-spacing: 1px;
    }
}
</style>
