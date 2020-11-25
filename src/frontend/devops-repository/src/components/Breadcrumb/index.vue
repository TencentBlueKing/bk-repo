<template>
    <div class="repo-breadcrumb flex-align-center">
        <div class="flex-align-center" v-if="list.length" v-bk-clickoutside="handleClickOutSide">
            <div class="ml10 hover-btn"
                v-for="(item, index) in list"
                :key="item.name"
                @click="($event) => showSelect(item, index, $event)"
                :class="{ 'pointer': item.list }">
                <template v-if="item.showSelect">
                    <bk-select class="breadcrumb-select" searchable :clearable="false" :value="item.value" @change="item.changeHandler">
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
                    <i v-if="item.list || index !== list.length - 1" class="ml10 devops-icon icon-angle-right"></i>
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
                if (e.target && e.target.className && e.target.className.includes('bk-option')) return
                this.list.forEach(item => {
                    this.$set(item, 'showSelect', false)
                })
            },
            showSelect (selected, index, $event) {
                this.list.forEach(item => {
                    this.$set(item, 'showSelect', false)
                })
                // 面包屑最后一项，提供下拉切换功能
                if (selected.list && selected.list.length && index === this.list.length - 1) {
                    this.$set(selected, 'showSelect', true)
                    this.$nextTick(() => {
                        const parent = this.$el.querySelector('.breadcrumb-select')
                        parent.addEventListener('click', e => {
                            e.stopPropagation()
                        }, { capture: false, once: true })
                        parent.querySelector('.bk-select-name').click()
                    })
                } else {
                    selected.cilckHandler && selected.cilckHandler(selected)
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
