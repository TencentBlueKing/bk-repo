<template>
    <div
        class="type-select-container flex-center"
        :class="{ 'active': showDropdown }"
        @click="showDropdown = !showDropdown"
        v-bk-clickoutside="hiddenDropdown">
        <div class="flex-column flex-center">
            <Icon size="24" :name="repoType" />
            <span style="margin-top: -5px;">{{changeRepoType(repoType)}}</span>
        </div>
        <i class="ml10 devops-icon" :class="showDropdown ? 'icon-angle-up' : 'icon-angle-down'"></i>
        <div v-show="showDropdown" class="dropdown-list" @click.stop="() => {}">
            <bk-radio-group :value="repoType" class="repo-type-radio-group" @change="changeType">
                <bk-radio-button v-for="repo in repoList" :key="repo.value" :value="repo.value" :disabled="disCheck(repo.value)">
                    <div class="flex-column flex-center repo-type-radio">
                        <Icon size="32" :name="repo.value" />
                        <span>{{repo.label}}</span>
                    </div>
                </bk-radio-button>
            </bk-radio-group>
        </div>
    </div>
</template>
<script>
    import { ciDisableRepoEnum } from '@repository/store/publicEnum'

    export default {
        name: 'TypeSelect',
        props: {
            repoList: {
                type: Array,
                default: () => []
            },
            repoType: {
                type: String,
                default: 'generic'
            }
        },
        data () {
            return {
                showDropdown: false
            }
        },
        methods: {
            disCheck (repoName) {
                if (MODE_CONFIG !== 'ci') {
                    return false
                } else {
                    return ciDisableRepoEnum.includes(repoName)
                }
            },
            hiddenDropdown () {
                this.showDropdown = false
            },
            changeType (type) {
                this.$emit('change', type)
                this.hiddenDropdown()
            },
            changeRepoType (type) {
                if (this.repoList.length > 0) {
                    for (let i = 0; i < this.repoList.length; i++) {
                        if (this.repoList[i].value === type) {
                            return this.repoList[i].label
                        }
                    }
                }
                return 'Generic'
            }
        }
    }
</script>
<style lang="scss" scoped>
::v-deep .bk-form-radio-button .bk-radio-button-input:disabled+.bk-radio-button-text {
    position: relative;
    color: #dcdee5;
    background: #fafbfd;
    border-color: currentColor;
    border-left: 1px solid;
}
.type-select-container {
    position: relative;
    width: 111px;
    height: 48px;
    margin-right: -1px;
    border-radius: 2px 0 0 2px;
    border: 1px solid var(--borderWeightColor);
    cursor: pointer;
    &.active {
        color: var(--primaryColor);
        border-color: var(--primaryColor);
        z-index: 1;
    }
    .icon-angle-up,
    .icon-angle-down {
        font-size: 12px;
        font-weight: bold;
        color: var(--fontSubsidiaryColor);
        transform: scale(0.8)
    }
    .dropdown-list {
        position: absolute;
        top: calc(100% + 10px);
        left: 0;
        width: 580px;
        padding: 30px;
        background-color: white;
        box-shadow: 0px 0px 6px 0px rgba(167, 167, 167, 0.5);
        z-index: 1;
        cursor: default;
        .repo-type-radio-group {
            display: grid;
            grid-template: auto / repeat(5, 88px);
            gap: 20px;
            ::v-deep .bk-form-radio-button {
                .bk-radio-button-text {
                    height: auto;
                    line-height: initial;
                    padding: 0;
                    border-radius: 2px;
                }
            }
            .repo-type-radio {
                position: relative;
                padding: 5px;
                width: 88px;
                height: 66px;
            }
        }
    }
}
</style>
