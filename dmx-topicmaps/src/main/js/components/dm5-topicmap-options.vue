<template>
  <div class="dm5-topicmap-options">
    <div class="field-label">主题图类型</div>
    <el-select v-model="topicmapTypeUri">
      <el-option v-for="type in topicmapTypes" :label="type.name" :value="type.uri" :key="type.uri"></el-option>
    </el-select>
  </div>
</template>

<script>
export default {

  data () {
    return {
      topicmapTypeUri: 'dmx.topicmaps.topicmap'
    }
  },

  computed: {
    topicmapTypes () {
      return this.$store.state.topicmaps.topicmapTypes
    }
  }
}
</script>

<style>
</style>
