const state = {

  visible: false,     // Detail panel visibility

  tab: 'info',        // Selected tab: 'info', 'related', 'meta', 'view'.
                      // Note: form edit takes place in "info" tab, while 'mode' is set to 'form'.

  mode: 'info'        // Mode of the "info" tab: 'info' or 'form'.
}

const actions = {

  // TODO: combine with selectDetail action?
  setDetailPanelVisibility (_, visible) {
    // console.log('setDetailPanelVisibility', visible)
    // Note: we expect actual boolean values (not truish/falsish).
    // The watcher is supposed to fire only on actual visibility changes (see plugin.js).
    if (typeof visible !== 'boolean') {
      throw Error(`期望是布尔型, got ${typeof visible}`)
    }
    state.visible = visible
  },

  selectDetail (_, detail) {
    // console.log('selectDetail', detail)
    if (!['info', 'edit', 'related', 'meta', 'view'].includes(detail)) {
      throw Error(`"${detail}" 不是期望的详细名称。`)
    }
    if (detail === 'info') {
      state.tab = 'info'
      state.mode = 'info'
    } else if (detail === 'edit') {
      state.tab = 'info'
      state.mode = 'form'
    } else {
      state.tab = detail
    }
  }
}

export default {
  state,
  actions
}
