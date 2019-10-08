export default {

  storeModule: {
    name: 'geomaps',
    module: require('./geomaps').default
  },

  topicmapType: {
    uri: 'dmx.geomaps.geomap',
    name: "地理图谱",
    renderer: () => import('./dm5-geomap-renderer' /* webpackChunkName: "leaflet" */)
  }
}
