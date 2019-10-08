<template>
  <div class="dm5-workspace-options">
    <div class="field-label">共享模式</div>
    <el-radio-group v-model="sharingModeUri">
      <el-radio label="dmx.workspaces.private">私人模式</el-radio>
      <el-radio label="dmx.workspaces.confidential">保密模式</el-radio>
      <el-radio label="dmx.workspaces.collaborative">协作模式</el-radio>
      <el-radio label="dmx.workspaces.public">公众模式</el-radio>
      <el-radio label="dmx.workspaces.common">完全公开</el-radio>
    </el-radio-group>
  </div>
</template>

<script>
export default {

  created () {
    // console.log('dm5-workspace-options created')
  },

  mounted () {
    // console.log('dm5-workspace-options mounted')
  },

  data () {
    return {
      sharingModeUri: 'dmx.workspaces.private'
    }
  }
}
</script>

<style>
.dm5-workspace-options .el-radio-group .el-radio {
  display: block;       /* was inline-block */
  margin-left: 0;       /* was 30px (.el-radio+.el-radio) */
  line-height: 1.8;     /* was 1 */
}
</style>
