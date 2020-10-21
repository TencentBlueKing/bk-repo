<template>
    <div class="repo-select-container" v-bk-clickoutside="handleClickOutSide">
        <div class="repo-select-title hover-btn flex-align-center" @click="handleClick">
            <icon size="24" :name="selectedItem.type"></icon>
            <span class="ml10">{{selectedItem.name}}</span>
            <i class="ml10 devops-icon icon-angle-right" :class="{ 'angle-down': showList }"></i>
        </div>
        <div class="repo-select-main" :style="`height: ${ showList ? '370px' : '0' }`">
            <div class="repo-select-search">
                <bk-input
                    class="docker-tag-search"
                    v-model="repoNameInput"
                    :placeholder="$t('pleaseInput') + $t('repository') + $t('name')"
                    clearable>
                </bk-input>
            </div>
            <ul class="repo-select-list">
                <li class="hover-btn" v-for="repo in filterRepoList" :key="repo.name">
                    <div class="repo-item flex-align-center" :class="repo.name === selectedItem.name ? 'selected' : ''"
                        @click="handlerRepoClick(repo)">
                        <icon size="24" :name="repo.type"></icon>
                        <span class="ml10">{{repo.name}}</span>
                    </div>
                </li>
            </ul>
            <div class="repo-select-link flex-align-center">
                <router-link class="flex-center" :to="{ name: 'createRepo' }">
                    <i class="mr5 devops-icon icon-plus"></i>
                    {{$t('create') + $t('repository')}}
                </router-link>
                <router-link class="flex-center" :to="{ name: 'repoList' }">
                    <i class="mr5 devops-icon icon-list"></i>
                    {{$t('repoList')}}
                </router-link>
            </div>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'repoSelect',
        props: {
            repoName: {
                type: String,
                default: ''
            },
            repoList: {
                type: Array,
                default: []
            },
            hasClick: {
                type: Boolean,
                default: false
            }
        },
        data () {
            return {
                repoNameInput: '',
                showList: false
            }
        },
        computed: {
            filterRepoList () {
                return this.repoList.filter(v => v.name.includes(this.repoNameInput))
            },
            selectedItem () {
                return this.repoList.find(v => v.name === this.repoName) || {}
            }
        },
        methods: {
            handleClick () {
                if (this.hasClick) this.$emit('repo-click', this.repoList.find(v => v.name === this.repoName))
                else this.showList = !this.showList
            },
            handlerRepoClick (repo) {
                this.showList = false
                this.$emit('repo-change', repo)
            },
            handleClickOutSide () {
                this.showList = false
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-select-container {
    position: relative;
    height: 50px;
    display: flex;
    .repo-select-title {
        .icon-angle-right {
            transition: all .3s;
        }
        .angle-down {
            transform: rotate(90deg);
        }
    }
    .repo-select-main {
        position: absolute;
        margin-left: 15px;
        margin-top: 50px;
        width: 250px;
        overflow: hidden;
        border: solid $borderWeightColor;
        border-width: 0 1px;
        background-color: white;
        z-index: 1;
        transition: all .3s;
        box-shadow: 6px 6px 5px $boxShadowColor;
        .repo-select-search {
            padding: 10px;
            border-top: 1px solid $borderWeightColor;
        }
        .repo-select-list {
            height: calc(100% - 98px);
            border-top: 1px solid $borderWeightColor;
            overflow: auto;
            .repo-item {
                height: 45px;
                padding: 0 20px;
                &.selected, &:hover {
                    background-color: $bgHoverColor;
                }
            }
        }
        .repo-select-link {
            height: 45px;
            border: solid $borderWeightColor;
            border-width: 1px 0;
            a {
                height: 100%;
                flex: 1;
                color: $fontWeightColor;
                & + a {
                    border-left: 1px solid $borderWeightColor;
                }
                &:hover {
                    color: $primaryColor;
                    background-color: $bgHoverColor;
                }
            }
        }
    }
}
</style>
