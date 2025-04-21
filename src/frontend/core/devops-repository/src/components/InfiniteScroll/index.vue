<template>
    <div class="infinite-scroll-container">
        <div class="infinite-scroll-list" :class="{ 'need-scroll': needScroll }">
            <slot></slot>
            <div v-if="!hasNext" class="loading-more">{{ $t('No More')}}</div>
            <div v-else class="loading-more"><i class="devops-icon icon-circle-2-1 spin-icon"></i>{{ $t('Loading')}}</div>
        </div>
    </div>
</template>

<script>
    import { throttle } from '@repository/utils'
    const SCROLL_THRESHOLD = 100
    export default {
        name: 'InfiniteScroll',
        props: {
            isLoading: Boolean,
            hasNext: Boolean
        },
        data () {
            return {
                needScroll: false,
                resizeFn: null
            }
        },
        watch: {
            isLoading (val) {
                // 内容加载完成
                if (!val) {
                    this.calcNeedScroll()
                }
            }
        },
        mounted () {
            this.$el.querySelector('.infinite-scroll-list').addEventListener('scroll', throttle(this.handleScroll, 500))
            this.resizeFn = throttle(this.calcNeedScroll)
            window.addEventListener('resize', this.resizeFn)
        },
        beforeDestroy () {
            window.removeEventListener('resize', this.resizeFn)
        },
        methods: {
            handleScroll () {
                const { hasNext, isLoading } = this
                if (!hasNext || isLoading) return
                const target = this.$el.querySelector('.infinite-scroll-list')
                const offset = target.scrollHeight - (target.offsetHeight + target.scrollTop)
                if (offset <= SCROLL_THRESHOLD) { // scroll to end
                    this.$emit('load')
                }
            },
            scrollToTop () {
                const target = this.$el.querySelector('.infinite-scroll-list')
                target.scrollTo(0, 0)
            },
            calcNeedScroll () {
                const target = this.$el.querySelector('.infinite-scroll-list')
                this.needScroll = target.scrollHeight > target.offsetHeight
            }
        }
    }
</script>

<style lang="scss" scoped>
.infinite-scroll-container {
    height: 100%;
    overflow: hidden;
    margin-right: -10px;
    padding-right: 10px;
    .infinite-scroll-list {
        height: 100%;
        overflow-y: auto;
        &.need-scroll {
            margin-right: -10px;
            padding-right: 10px;
        }
        .loading-more {
            display: flex;
            justify-content: center;
            align-items: center;
            color: var(--fontDisableColor);
            .icon-circle-2-1 {
                margin-right: 8px;
            }
        }
    }
}
</style>
