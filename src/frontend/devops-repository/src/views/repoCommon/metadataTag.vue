<template>
    <ul class="metadata-tag">
        <template v-if="Array.isArray(metadata)">
            <li v-for="(item, index) in metadata.filter(item => item.system === true)" :key="index">
                <span class="key">{{ item.key }}</span>
                <span class="green">{{ item.value }}</span>
            </li>
        </template>
        <li v-else>
            <span class="key">{{ metadata.key }}</span>
            <span :class="getColor">{{ metadata.value }}</span>
        </li>
    </ul>
</template>

<script>
    export default {
        props: {
            metadata: {
                type: [Array, Object]
            }
        },
        computed: {
            getColor () {
                const { system } = this.metadata
                switch (system) {
                    case true:
                        return 'green'
                    case false:
                        return 'red'
                    default:
                        return 'blue'
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
.metadata-tag {
    display: flex;
    
    li {
        border-radius: 2px;
        display: flex;
        overflow: hidden;
        margin-right: 10px;
    }

    span {
        display: block;
        height: 20px;
        line-height: 20px;
        color: #FFF;
        font-size: 12px;
        padding: 0 8px;

        &.key {
            background-color: #363c5e;
        }

        &.blue {
            background-color: #1283C4;
        }
        &.green {
            background-color: #4CC71F;
        }
        &.red {
            background-color: #CA553E;
        }
    }
}
</style>
