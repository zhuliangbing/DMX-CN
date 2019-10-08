package systems.dmx.topicmaps;

import systems.dmx.core.Topic;
import systems.dmx.core.model.topicmaps.ViewProps;
import systems.dmx.core.service.CoreService;



class DefaultTopicmapType implements TopicmapType, TopicmapsConstants {

    @Override
    public String getUri() {
        return TOPICMAP;
    }

    @Override
    public void initTopicmapState(Topic topicmapTopic, ViewProps viewProps, CoreService dmx) {
        dmx.getModelFactory().newViewProps()
            .put(PROP_PAN_X, 0)
            .put(PROP_PAN_Y, 0)
            .put(PROP_ZOOM, 1.0)
            .store(topicmapTopic);
    }
}
