<template>
    <div class="repo-breadcrumb flex-align-center">
        <div class="ml10 hover-btn"
            v-for="(item, index) in list"
            :key="index"
            @click="() => item.cilckHandler && item.cilckHandler(item)">
            <span class="breadcrumb-value">{{ item.name }}</span>
            <i v-if="item.list || index !== list.length - 1" class="ml10 devops-icon icon-angle-right"></i>
        </div>
    </div>
</template>
<script>
    export default {
        name: 'breadcrumb',
        props: {
            list: {
                type: Array,
                default: () => []
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
