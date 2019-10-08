package systems.dmx.topicmaps;

import systems.dmx.core.Assoc;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.model.topicmaps.ViewProps;
import systems.dmx.core.util.IdList;

import java.util.List;



public interface TopicmapsService extends TopicmapsConstants {

    // ------------------------------------------------------------------------------------------------------- Constants

    static final String DEFAULT_TOPICMAP_NAME     = "未定义";
    static final String DEFAULT_TOPICMAP_TYPE_URI = TOPICMAP;

    // -------------------------------------------------------------------------------------------------- Public Methods

    /**
     * @return  the created Topicmap topic.
     */
    Topic createTopicmap(String name, String topicmapTypeUri, ViewProps viewProps);

    // ---

    /**
     * Fetches a topicmap from DB.
     *
     * @param   includeChildren   if true the topics contained in the topicmap will include their child topics.
     */
    Topicmap getTopicmap(long topicmapId, boolean includeChildren);

    Assoc getTopicMapcontext(long topicmapId, long topicId);

    Assoc getAssocMapcontext(long topicmapId, long assocId);

    /**
     * Returns all topicmaps which contain the given topic/assoc.
     * Only those topicmaps are returned in which the given topic/assoc is <i>visible</i> (not hidden).
     *
     * @param   objectId    a topic ID or an assoc ID
     *
     * @return  topics of type Topicmap
     */
    List<RelatedTopic> getTopicmapTopics(long objectId);

    // ---

    // TODO: add addTopicToTopicmap() with default visibility (true)

    /**
     * Convenience method to add a topic with the standard view properties.
     */
    void addTopicToTopicmap(long topicmapId, long topicId, int x, int y, boolean visibility);

    /**
     * Adds a topic to a topicmap. If the topic is added already an exception is thrown.
     */
    void addTopicToTopicmap(long topicmapId, long topicId, ViewProps viewProps);

    // TODO: add addAssocToTopicmap() with default view props (visibility=true, pinned=false)

    /**
     * Adds an association to a topicmap. If the association is added already an exception is thrown.
     */
    void addAssocToTopicmap(long topicmapId, long assocId, ViewProps viewProps);

    // Note: this is needed in order to reveal a related topic in a *single* request. Otherwise client-sync might fail
    // due to asynchronicity. A client might receive the "addAssoc" WebSocket message *before* the "addTopic" message.
    void addRelatedTopicToTopicmap(long topicmapId, long topicId, long assocId, ViewProps viewProps);

    // ---

    void setTopicViewProps(long topicmapId, long topicId, ViewProps viewProps);

    void setAssocViewProps(long topicmapId, long assocId, ViewProps viewProps);

    /**
     * Convenience method to update the "dmx.topicmaps.x" and "dmx.topicmaps.y" standard view properties.
     */
    void setTopicPosition(long topicmapId, long topicId, int x, int y);

    void setTopicPositions(long topicmapId, TopicCoords coords);

    /**
     * Convenience method to update the "dmx.topicmaps.visibility" standard view property.
     */
    void setTopicVisibility(long topicmapId, long topicId, boolean visibility);

    /**
     * Convenience method to update the "dmx.topicmaps.visibility" standard view property.
     * ### FIXME: idempotence? If the associationn is not contained in the topicmap nothing is performed.
     */
    void setAssocVisibility(long topicmapId, long assocId, boolean visibility);

    // ---

    void hideTopics(long topicmapId, IdList topicIds);
    void hideAssocs(long topicmapId, IdList assocIds);
    void hideMulti(long topicmapId, IdList topicIds, IdList assocIds);

    // ---

    void setTopicmapViewport(long topicmapId, int panX, int panY, double zoom);

    // ---

    void registerTopicmapType(TopicmapType topicmapType);

    // ### TODO: unregister needed? Might a topicmap type hold a stale dmx instance?

    // ---

    void registerViewmodelCustomizer(ViewmodelCustomizer customizer);

    void unregisterViewmodelCustomizer(ViewmodelCustomizer customizer);
}
