<template>
    <ul class="metadata-tag">
        <template v-if="Array.isArray(metadata)">
            <template v-for="(item, index) in metadataList">
                <li :key="index" v-if="index < 2">
                    <span class="key">{{ item.key }}</span>
                    <span class="green" v-bk-overflow-tips>{{ item.value }}</span>
                </li>
            </template>
            <li v-if="metadataList.length > 2">
                <bk-popover placement="bottom" theme="light" trigger="click">
                    <i class="more-wrap"><Icon size="14" name="more" /></i>
                    <ul slot="content" class="popover-list">
                        <li v-for="(item, index) in metadataList.splice(2)" :key="index">
                            <span class="key">{{ item.key }}</span>
                            <span class="green" v-bk-overflow-tips>{{ item.value }}</span>
                        </li>
                    </ul>
                </bk-popover>
            </li>
        </template>
        <li v-else>
            <span class="key">{{ metadata.key }}</span>
            <span :class="getColor" v-bk-overflow-tips>{{ metadata.value }}</span>
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
            metadataList () {
                return this.metadata.filter(item => item.system === true)
            },
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
.metadata-tag,
.popover-list {
    li {
        border-radius: 4px;
        display: flex;
        overflow: hidden;
        margin: 5px 10px 5px 0;
        flex-shrink: 0;
        height: 20px;

        i.more-wrap {
            width: 20px;
            height: 20px;
            border-radius: 4px;
            background: rgba(58,132,255,0.1);
            cursor: pointer;
            display: flex;
            justify-content: center;
            align-items: center;
        }
    }

    span {
        display: block;
        height: 20px;
        line-height: 20px;
        color: #FFF;
        font-size: 12px;
        padding: 0 8px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        &:last-child {
            max-width: 150px;
        }

        &.key {
            background-color: #363c5e;
            background-image: linear-gradient(#606164, #4D4D4D);
        }

        &.blue {
            background-color: #1283C4;
            background-image: linear-gradient(#2F79BA, #1E68A8);
        }
        &.green {
            background-color: #4CC71F;
            background-image: linear-gradient(#66BF3F, #54AD2D);
        }
        &.red {
            background-color: #CA553E;
            background-image: linear-gradient(#CF6D57, #BD5B46);
        }
    }
}
.metadata-tag {
    display: flex;
}
.popover-list {
   li {
    margin: 5px 0;
   }
}
</style>
