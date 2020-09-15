<template>
    <div class="repo-breadcrumb flex-align-center">
        <div class="flex-align-center" v-if="list.length" v-bk-clickoutside="handleClickOutSide">
            <div class="ml10 hover-btn"
                v-for="(item, index) in list"
                :key="item.name"
                @click="showSelect(item, index)"
                :class="{ 'pointer': item.list }">
                <template v-if="item.showSelect">
                    <bk-select class="breadcrumb-select" :clearable="false" :value="item.value" @change="item.changeHandler">
                        <bk-option
                            v-for="option in item.list"
                            :key="option.name"
                            :id="option.value"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                </template>
                <template v-else>
                    <span class="breadcrumb-value">{{ item.name }}</span>
                    <i v-if="item.list" class="ml10 devops-icon icon-angle-right"></i>
                </template>
            </div>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'breadcrumb',
        props: {
            list: {
                type: Array,
                default: []
            }
        },
        methods: {
            handleClickOutSide (e) {
                if (e.target.className.includes('bk-option')) return
                this.list.forEach(item => {
                    this.$set(item, 'showSelect', false)
                })
            },
            showSelect (selected, index) {
                this.list.forEach(item => {
                    this.$set(item, 'showSelect', false)
                })
                if (selected.list && index === this.list.length - 1) {
                    this.$set(selected, 'showSelect', true)
                } else {
                    selected.cilckHandler(selected)
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-breadcrumb {
    .breadcrumb-select {
        width: 200px;
    }
    .pointer {
        user-select: none;
        cursor: pointer;
    }
}
</style>
