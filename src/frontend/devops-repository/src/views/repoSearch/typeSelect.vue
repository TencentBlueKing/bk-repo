<template>
    <div class="type-select-container flex-center"
        :class="{ 'active': showDropdown }"
        @click="showDropdown = !showDropdown"
        v-bk-clickoutside="hiddenDropdown">
        <Icon size="40" :name="repoType" />
        <i class="ml10 devops-icon" :class="showDropdown ? 'icon-up-shape' : 'icon-down-shape'"></i>
        <div v-show="showDropdown" class="dropdown-list" @click.stop="() => {}">
            <bk-radio-group :value="repoType" class="repo-type-radio-group" @change="changeType">
                <bk-radio-button v-for="repo in repoList" :key="repo" :value="repo">
                    <div class="flex-column flex-center repo-type-radio">
                        <Icon size="32" :name="repo" />
                        <span>{{repo}}</span>
                    </div>
                </bk-radio-button>
            </bk-radio-group>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'typeSelect',
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
            hiddenDropdown () {
                this.showDropdown = false
            },
            changeType (type) {
                this.$emit('change', type)
                this.hiddenDropdown()
            }
        }
    }
</script>
<style lang="scss" scoped>
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
            grid-gap: 20px;
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
