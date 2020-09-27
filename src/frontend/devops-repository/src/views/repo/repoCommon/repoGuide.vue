<template>
    <article class="repo-guide-container">
        <section v-for="section in article" :key="section.title">
            <h2 v-if="section.title" class="mb10 section-header">{{ section.title }}</h2>
            <div class="section-main">
                <div class="sub-section flex-column" v-for="block in section.main" :key="block.subTitle">
                    <span class="mb10">{{ block.subTitle }}</span>
                    <code-area :code-list="block.codeList"></code-area>
                </div>
            </div>
        </section>
    </article>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    export default {
        name: 'repoGuide',
        components: { CodeArea },
        props: {
            article: {
                type: Array,
                default: []
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-guide-container {
    section + section {
        margin-top: 20px;
    }
    .section-header {
        padding: 10px 20px;
        background-color: $bgLightColor;
    }
    .section-main {
        padding: 20px;
        border: 2px dashed $borderWeightColor;
        border-radius: 5px;
        .sub-section {
            & + .sub-section {
                margin-top: 20px;
            }
        }
    }
}
</style>
