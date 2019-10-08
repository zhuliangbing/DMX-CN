package systems.dmx.topicmaps;

import systems.dmx.core.Assoc;
import systems.dmx.core.DMXObject;
import systems.dmx.core.RelatedAssoc;
import systems.dmx.core.RelatedObject;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.topicmaps.ViewAssoc;
import systems.dmx.core.model.topicmaps.ViewTopic;
import systems.dmx.core.model.topicmaps.ViewProps;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.CoreService;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.util.DMXUtils;
import systems.dmx.core.util.IdList;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;



@Path("/topicmap")      // TODO: rename "/topicmaps"
@Consumes("application/json")
@Produces("application/json")
public class TopicmapsPlugin extends PluginActivator implements TopicmapsService, TopicmapsConstants, MessengerContext {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    /**
     * Topicmap Type registry.
     */
    private Map<String, TopicmapType> topicmapTypes = new HashMap();

    private List<ViewmodelCustomizer> viewmodelCustomizers = new ArrayList();

    private Messenger me = new Messenger(this);

    @Context
    private HttpServletRequest request;     // required by Messenger

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    public TopicmapsPlugin() {
        // Note: registering the default topicmap type in the init() hook would be too late.
        // The topicmap type is already needed at install-in-DB time ### Still true? Use preInstall() hook?
        registerTopicmapType(new DefaultTopicmapType());
    }



    // ************************
    // *** TopicmapsService ***
    // ************************



    @POST
    @Transactional
    @Override
    public Topic createTopicmap(@QueryParam("name") String name,
                                @QueryParam("topicmap_type_uri") String topicmapTypeUri,
                                ViewProps viewProps) {
        try {
            logger.info("Creating topicmap \"" + name + "\", topicmapTypeUri=\"" + topicmapTypeUri + "\", viewProps=" +
                viewProps);
            Topic topicmapTopic = dmx.createTopic(mf.newTopicModel(TOPICMAP, mf.newChildTopicsModel()
                .put("dmx.topicmaps.topicmap_name", name)
                .put("dmx.topicmaps.topicmap_type_uri", topicmapTypeUri)
            ));
            // init topicmap state
            getTopicmapType(topicmapTypeUri).initTopicmapState(topicmapTopic, viewProps, dmx);
            //
            me.newTopicmap(topicmapTopic);      // FIXME: broadcast to eligible users only
            return topicmapTopic;
        } catch (Exception e) {
            throw new RuntimeException("Creating topicmap \"" + name + "\" failed, topicmapTypeUri=\"" +
                topicmapTypeUri + "\"", e);
        }
    }

    // ---

    @GET
    @Path("/{id}")
    @Override
    public Topicmap getTopicmap(@PathParam("id") long topicmapId, @QueryParam("children") boolean includeChildren) {
        try {
            logger.info("Fetching topicmap " + topicmapId + ", includeChildren=" + includeChildren);
            // Note: a Topicmap is not a DMXObject. So the JerseyResponseFilter's automatic
            // child topic loading is not applied. We must load the child topics manually here.
            Topic topicmapTopic = dmx.getTopic(topicmapId).loadChildTopics();
            return new Topicmap(
                topicmapTopic.getModel(),
                fetchTopicmapViewProps(topicmapTopic),
                fetchTopics(topicmapTopic, includeChildren),
                fetchAssocs(topicmapTopic)
            );
        } catch (Exception e) {
            throw new RuntimeException("Fetching topicmap " + topicmapId + " failed", e);
        }
    }

    @Override
    public Assoc getTopicMapcontext(long topicmapId, long topicId) {
        return dmx.getAssoc(TOPICMAP_CONTEXT, topicmapId, topicId, ROLE_TYPE_TOPICMAP, ROLE_TYPE_CONTENT);
    }

    @Override
    public Assoc getAssocMapcontext(long topicmapId, long assocId) {
        return dmx.getAssocBetweenTopicAndAssoc(TOPICMAP_CONTEXT, topicmapId, assocId, ROLE_TYPE_TOPICMAP,
            ROLE_TYPE_CONTENT);
    }

    @GET
    @Path("/object/{id}")
    @Override
    public List<RelatedTopic> getTopicmapTopics(@PathParam("id") long objectId) {
        try {
            List<RelatedTopic> topicmapTopics = new ArrayList();
            for (RelatedTopic topic : dmx.getObject(objectId).getRelatedTopics(TOPICMAP_CONTEXT, ROLE_TYPE_CONTENT,
                                                                               ROLE_TYPE_TOPICMAP, TOPICMAP)) {
                if (visibility(topic.getRelatingAssoc())) {
                    topicmapTopics.add(topic);
                }
            }
            return topicmapTopics;
        } catch (Exception e) {
            throw new RuntimeException("Fetching topicmap topics of topic/assoc " + objectId + " failed", e);
        }
    }

    // ---

    @Override
    public void addTopicToTopicmap(long topicmapId, long topicId, int x, int y, boolean visibility) {
        addTopicToTopicmap(topicmapId, topicId, mf.newViewProps(x, y, visibility, false));   // pinned=false
    }

    @POST
    @Path("/{id}/topic/{topic_id}")
    @Transactional
    @Override
    public void addTopicToTopicmap(@PathParam("id") final long topicmapId,
                                   @PathParam("topic_id") final long topicId, final ViewProps viewProps) {
        try {
            // Note: a Mapcontext association must have no workspace assignment as it is "system" owned
            dmx.getPrivilegedAccess().runWithoutWorkspaceAssignment(new Callable<Void>() {  // throws Exception
                @Override
                public Void call() {
                    if (getTopicMapcontext(topicmapId, topicId) != null) {      // TODO: idempotence?
                        throw new RuntimeException("Topic " + topicId + " already added to topicmap" + topicmapId);
                    }
                    createTopicMapcontext(topicmapId, topicId, viewProps);
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Adding topic " + topicId + " to topicmap " + topicmapId + " failed, " +
                "viewProps=" + viewProps, e);
        }
    }

    @POST
    @Path("/{id}/association/{assoc_id}")
    @Transactional
    @Override
    public void addAssocToTopicmap(@PathParam("id") final long topicmapId,
                                   @PathParam("assoc_id") final long assocId, final ViewProps viewProps) {
        try {
            // Note: a Mapcontext association must have no workspace assignment as it is "system" owned
            dmx.getPrivilegedAccess().runWithoutWorkspaceAssignment(new Callable<Void>() {  // throws Exception
                @Override
                public Void call() {
                    if (getAssocMapcontext(topicmapId, assocId) != null) {      // TODO: idempotence?
                        throw new RuntimeException("Assoc " + assocId + " already added to topicmap " + topicmapId);
                    }
                    createAssocMapcontext(topicmapId, assocId, viewProps);
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Adding association " + assocId + " to topicmap " + topicmapId + " failed, " +
                "viewProps=" + viewProps, e);
        }
    }

    @POST
    @Path("/{id}/topic/{topic_id}/association/{assoc_id}")
    @Transactional
    @Override
    public void addRelatedTopicToTopicmap(@PathParam("id") final long topicmapId,
                                          @PathParam("topic_id") final long topicId,
                                          @PathParam("assoc_id") final long assocId, final ViewProps viewProps) {
        try {
            // Note: a Mapcontext association must have no workspace assignment as it is "system" owned
            dmx.getPrivilegedAccess().runWithoutWorkspaceAssignment(new Callable<Void>() {  // throws Exception
                @Override
                public Void call() {
                    // 1) add topic
                    Assoc topicMapcontext = getTopicMapcontext(topicmapId, topicId);
                    if (topicMapcontext == null) {
                        createTopicMapcontext(topicmapId, topicId, viewProps);
                    } else if (!visibility(topicMapcontext)) {
                        _setTopicVisibility(topicmapId, topicId, true, topicMapcontext);
                    }
                    // 2) add association
                    Assoc assocMapcontext = getAssocMapcontext(topicmapId, assocId);
                    if (assocMapcontext == null) {
                        createAssocMapcontext(topicmapId, assocId, mf.newViewProps(true, false));
                    } else if (!visibility(assocMapcontext)) {
                        _setAssocVisibility(topicmapId, assocId, true, assocMapcontext);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Adding related topic " + topicId + " (assocId=" + assocId + ") to topicmap " +
                topicmapId + " failed, viewProps=" + viewProps, e);
        }
    }

    // ---

    @PUT
    @Path("/{id}/topic/{topic_id}")
    @Transactional
    @Override
    public void setTopicViewProps(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                  ViewProps viewProps) {
        storeTopicViewProps(topicmapId, topicId, viewProps);
    }

    @PUT
    @Path("/{id}/association/{assoc_id}")
    @Transactional
    @Override
    public void setAssocViewProps(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId,
                                  ViewProps viewProps) {
        storeAssocViewProps(topicmapId, assocId, viewProps);
    }

    @PUT
    @Path("/{id}/topic/{topic_id}/{x}/{y}")         // TODO: extend URL .../x/{x}/y/{y}
    @Transactional
    @Override
    public void setTopicPosition(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                   @PathParam("x") int x, @PathParam("y") int y) {
        try {
            storeTopicViewProps(topicmapId, topicId, mf.newViewProps(x, y));
            me.setTopicPosition(topicmapId, topicId, x, y);
        } catch (Exception e) {
            throw new RuntimeException("Setting position of topic " + topicId + " in topicmap " + topicmapId +
                " failed", e);
        }
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Override
    public void setTopicPositions(@PathParam("id") long topicmapId, TopicCoords coords) {
        for (TopicCoords.Entry entry : coords) {
            setTopicPosition(topicmapId, entry.topicId, entry.x, entry.y);
        }
    }

    @PUT
    @Path("/{id}/topic/{topic_id}/{visibility}")    // TODO: extend URL .../visibility/{visibility}
    @Transactional
    @Override
    public void setTopicVisibility(@PathParam("id") long topicmapId, @PathParam("topic_id") long topicId,
                                                                     @PathParam("visibility") boolean visibility) {
        try {
            // TODO: idempotence?
            _setTopicVisibility(topicmapId, topicId, visibility, fetchTopicMapcontext(topicmapId, topicId));
        } catch (Exception e) {
            throw new RuntimeException("Setting visibility of topic " + topicId + " in topicmap " + topicmapId +
                " failed", e);
        }
    }

    @PUT
    @Path("/{id}/assoc/{assoc_id}/visibility/{visibility}")
    @Transactional
    @Override
    public void setAssocVisibility(@PathParam("id") long topicmapId, @PathParam("assoc_id") long assocId,
                                                                     @PathParam("visibility") boolean visibility) {
        try {
            Assoc assocMapcontext = getAssocMapcontext(topicmapId, assocId);
            if (assocMapcontext != null) {      // Note: idempotence is needed for hide-multi
                _setAssocVisibility(topicmapId, assocId, visibility, assocMapcontext);
            }
        } catch (Exception e) {
            throw new RuntimeException("Setting visibility of assoc " + assocId + " in topicmap " + topicmapId +
                " failed", e);
        }
    }

    // ---

    @PUT
    @Path("/{id}/topics/{topicIds}/visibility/false")
    @Transactional
    @Override
    public void hideTopics(@PathParam("id") long topicmapId, @PathParam("topicIds") IdList topicIds) {
        hideMulti(topicmapId, topicIds, new IdList());
    }

    @PUT
    @Path("/{id}/assocs/{assocIds}/visibility/false")
    @Transactional
    @Override
    public void hideAssocs(@PathParam("id") long topicmapId, @PathParam("assocIds") IdList assocIds) {
        hideMulti(topicmapId, new IdList(), assocIds);
    }

    @PUT
    @Path("/{id}/topics/{topicIds}/assocs/{assocIds}/visibility/false")
    @Transactional
    @Override
    public void hideMulti(@PathParam("id") long topicmapId, @PathParam("topicIds") IdList topicIds,
                                                            @PathParam("assocIds") IdList assocIds) {
        logger.info("topicmapId=" + topicmapId + ", topicIds=" + topicIds + ", assocIds=" + assocIds);
        for (long id : topicIds) {
            setTopicVisibility(topicmapId, id, false);
        }
        for (long id : assocIds) {
            setAssocVisibility(topicmapId, id, false);
        }
    }

    // ---

    @PUT
    @Path("/{id}/pan/{x}/{y}/zoom/{zoom}")
    @Transactional
    @Override
    public void setTopicmapViewport(@PathParam("id") long topicmapId, @PathParam("x") int panX,
                                                                      @PathParam("y") int panY,
                                                                      @PathParam("zoom") double zoom) {
        try {
            mf.newViewProps()
                .put(PROP_PAN_X, panX)
                .put(PROP_PAN_Y, panY)
                .put(PROP_ZOOM, zoom)
                .store(dmx.getTopic(topicmapId));
        } catch (Exception e) {
            throw new RuntimeException("Setting viewport of topicmap " + topicmapId + " failed, panX=" + panX +
                ", panY=" + panY + ", zoom=" + zoom, e);
        }
    }

    // ---

    @Override
    public void registerTopicmapType(TopicmapType topicmapType) {
        logger.info("### Registering topicmap type \"" + topicmapType.getClass().getName() + "\"");
        topicmapTypes.put(topicmapType.getUri(), topicmapType);
    }

    // ---

    @Override
    public void registerViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Registering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        viewmodelCustomizers.add(customizer);
    }

    @Override
    public void unregisterViewmodelCustomizer(ViewmodelCustomizer customizer) {
        logger.info("### Unregistering viewmodel customizer \"" + customizer.getClass().getName() + "\"");
        if (!viewmodelCustomizers.remove(customizer)) {
            throw new RuntimeException("Unregistering viewmodel customizer failed, customizer=" + customizer);
        }
    }

    // ---

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}")
    @Produces("text/html")
    public InputStream getTopicmapInWebclient() {
        // Note: the path parameter is evaluated at client-side
        return invokeWebclient();
    }

    // Note: not part of topicmaps service
    @GET
    @Path("/{id}/topic/{topic_id}")
    @Produces("text/html")
    public InputStream getTopicmapAndTopicInWebclient() {
        // Note: the path parameters are evaluated at client-side
        return invokeWebclient();
    }



    // ************************
    // *** MessengerContext ***
    // ************************



    @Override
    public CoreService getCoreService() {
        return dmx;
    }

    @Override
    public HttpServletRequest getRequest() {
        return request;
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    // --- Fetch Topicmap ---

    private Map<Long, ViewTopic> fetchTopics(Topic topicmapTopic, boolean includeChildren) {
        Map<Long, ViewTopic> topics = new HashMap();
        List<RelatedTopic> relTopics = topicmapTopic.getRelatedTopics(TOPICMAP_CONTEXT,
            ROLE_TYPE_TOPICMAP, ROLE_TYPE_CONTENT, null);       // othersTopicTypeUri=null
        if (includeChildren) {
            DMXUtils.loadChildTopics(relTopics);
        }
        for (RelatedTopic topic : relTopics) {
            topics.put(topic.getId(), buildViewTopic(topic));
        }
        return topics;
    }

    private Map<Long, ViewAssoc> fetchAssocs(Topic topicmapTopic) {
        Map<Long, ViewAssoc> assocs = new HashMap();
        List<RelatedAssoc> relAssocs = topicmapTopic.getRelatedAssocs(TOPICMAP_CONTEXT, ROLE_TYPE_TOPICMAP,
            ROLE_TYPE_CONTENT, null);       // othersAsspcTypeUri=null
        for (RelatedAssoc assoc : relAssocs) {
            assocs.put(assoc.getId(), buildViewAssoc(assoc));
        }
        return assocs;
    }

    // ---

    private ViewTopic buildViewTopic(RelatedTopic topic) {
        try {
            ViewProps viewProps = fetchTopicViewProps(topic.getRelatingAssoc());
            invokeViewmodelCustomizers(topic, viewProps);
            return mf.newViewTopic(topic.getModel(), viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Building ViewTopic " + topic.getId() + " failed", e);
        }
    }

    private ViewAssoc buildViewAssoc(RelatedAssoc assoc) {
        try {
            ViewProps viewProps = fetchAssocViewProps(assoc.getRelatingAssoc());
            // invokeViewmodelCustomizers(assoc, viewProps);    // TODO: assoc customizers?
            return mf.newViewAssoc(assoc.getModel(), viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Building ViewAssoc " + assoc.getId() + " failed", e);
        }
    }

    // --- Fetch View Properties ---

    private ViewProps fetchTopicmapViewProps(Topic topicmapTopic) {
        return mf.newViewProps()
            .put(PROP_PAN_X, topicmapTopic.getProperty(PROP_PAN_X))
            .put(PROP_PAN_Y, topicmapTopic.getProperty(PROP_PAN_Y))
            .put(PROP_ZOOM,  topicmapTopic.getProperty(PROP_ZOOM));
    }

    private ViewProps fetchTopicViewProps(Assoc topicmapContext) {
        return mf.newViewProps(
            (Integer) topicmapContext.getProperty(PROP_X),
            (Integer) topicmapContext.getProperty(PROP_Y),
            visibility(topicmapContext),
            pinned(topicmapContext)
        );
    }

    private ViewProps fetchAssocViewProps(Assoc topicmapContext) {
        return mf.newViewProps(
            visibility(topicmapContext),
            pinned(topicmapContext)
        );
    }

    private boolean visibility(Assoc topicmapContext) {
        return (Boolean) topicmapContext.getProperty(PROP_VISIBILITY);
    }

    private boolean pinned(Assoc topicmapContext) {
        return (Boolean) topicmapContext.getProperty(PROP_PINNED);
    }

    // --- Update Visibility ---

    private void _setTopicVisibility(long topicmapId, long topicId, boolean visibility, Assoc topicmapContext) {
        Topic topic = dmx.getTopic(topicId);
        ViewProps viewProps = mf.newViewProps(visibility);
        if (visibility) {
            autoRevealAssocs(topic, topicmapId);
        } else {
            autoHideAssocs(topic, topicmapId);
            viewProps.put(PROP_PINNED, false);      // hide implies unpin
        }
        // update DB
        viewProps.store(topicmapContext);
        // send message
        me.setTopicVisibility(topicmapId, topicId, visibility);
    }

    private void _setAssocVisibility(long topicmapId, long assocId, boolean visibility, Assoc topicmapContext) {
        Assoc assoc = dmx.getAssoc(assocId);
        // update DB
        if (visibility) {
            autoRevealAssocs(assoc, topicmapId);
            mf.newViewProps(visibility).store(topicmapContext);
        } else {
            // Note: topicmap contexts of *explicitly* hidden assocs are removed
            deleteAllAssocMapcontexts(assoc, topicmapId);
            deleteAssocMapcontext(topicmapContext);
        }
        // send message
        me.setAssocVisibility(topicmapId, assocId, visibility);
    }

    private void autoRevealAssocs(DMXObject object, long topicmapId) {
        for (RelatedTopic topic : object.getRelatedTopics()) {
            _autoRevealAssocs(topic, getTopicMapcontext(topicmapId, topic.getId()), topicmapId);
        }
        for (RelatedAssoc assoc : object.getRelatedAssocs()) {
            _autoRevealAssocs(assoc, getAssocMapcontext(topicmapId, assoc.getId()), topicmapId);
        }
    }

    private void _autoRevealAssocs(RelatedObject object, Assoc topicmapContext, long topicmapId) {
        if (topicmapContext != null && visibility(topicmapContext)) {
            Assoc assoc = object.getRelatingAssoc();
            Assoc assocMapcontext = getAssocMapcontext(topicmapId, assoc.getId());
            if (assocMapcontext != null && !visibility(assocMapcontext)) {
                // update DB
                mf.newViewProps(true).store(assocMapcontext);       // visibility=true
                // recursion
                autoRevealAssocs(assoc, topicmapId);
            }
        }
    }

    private void autoHideAssocs(DMXObject object, long topicmapId) {
        for (Assoc assoc : object.getAssocs()) {
            Assoc topicmapContext = getAssocMapcontext(topicmapId, assoc.getId());
            if (topicmapContext != null) {
                // update DB
                mf.newViewProps(false, false).store(topicmapContext);       // visibility=false, pinned=false
                // recursion
                autoHideAssocs(assoc, topicmapId);
            }
        }
    }

    private void deleteAllAssocMapcontexts(Assoc object, long topicmapId) {
        for (Assoc assoc : object.getAssocs()) {
            Assoc assocMapcontext = getAssocMapcontext(topicmapId, assoc.getId());
            if (assocMapcontext != null) {
                deleteAssocMapcontext(assocMapcontext);
                deleteAllAssocMapcontexts(assoc, topicmapId);     // recursion
            }
        }
    }

    private void deleteAssocMapcontext(Assoc assocMapcontext) {
        // Note: a mapcontext association has no workspace assignment -- it belongs to the system.
        // Deleting a mapcontext association is a privileged operation.
        dmx.getPrivilegedAccess().deleteAssocMapcontext(assocMapcontext);
    }

    // --- Store View Properties ---

    /**
     * Convenience when you don't have a topicmap context already.
     */
    private void storeTopicViewProps(long topicmapId, long topicId, ViewProps viewProps) {
        try {
            viewProps.store(fetchTopicMapcontext(topicmapId, topicId));
        } catch (Exception e) {
            throw new RuntimeException("Storing view props of topic " + topicId + " failed, viewProps=" + viewProps, e);
        }
    }

    /**
     * Convenience when you don't have a topicmap context already.
     */
    private void storeAssocViewProps(long topicmapId, long assocId, ViewProps viewProps) {
        try {
            viewProps.store(fetchAssocMapcontext(topicmapId, assocId));
        } catch (Exception e) {
            throw new RuntimeException("Storing view props of assoc " + assocId + " failed, viewProps=" + viewProps, e);
        }
    }

    // --- Topicmap Contexts ---

    private Assoc fetchTopicMapcontext(long topicmapId, long topicId) {
        Assoc topicmapContext = getTopicMapcontext(topicmapId, topicId);
        if (topicmapContext == null) {
            throw new RuntimeException("Topic " + topicId + " not contained in topicmap " + topicmapId);
        }
        return topicmapContext;
    }

    private Assoc fetchAssocMapcontext(long topicmapId, long assocId) {
        Assoc topicmapContext = getAssocMapcontext(topicmapId, assocId);
        if (topicmapContext == null) {
            throw new RuntimeException("Assoc " + assocId + " not contained in topicmap " + topicmapId);
        }
        return topicmapContext;
    }

    // ---

    private void createTopicMapcontext(long topicmapId, long topicId, ViewProps viewProps) {
        Assoc topicMapcontext = dmx.createAssoc(mf.newAssocModel(TOPICMAP_CONTEXT,
            mf.newTopicPlayerModel(topicmapId, ROLE_TYPE_TOPICMAP),
            mf.newTopicPlayerModel(topicId,    ROLE_TYPE_CONTENT)
        ));
        viewProps.store(topicMapcontext);
        //
        ViewTopic topic = mf.newViewTopic(dmx.getTopic(topicId).getModel(), viewProps);
        me.addTopicToTopicmap(topicmapId, topic);
    }

    private void createAssocMapcontext(long topicmapId, long assocId, ViewProps viewProps) {
        Assoc assocMapcontext = dmx.createAssoc(mf.newAssocModel(TOPICMAP_CONTEXT,
            mf.newTopicPlayerModel(topicmapId, ROLE_TYPE_TOPICMAP),
            mf.newAssocPlayerModel(assocId,    ROLE_TYPE_CONTENT)
        ));
        viewProps.store(assocMapcontext);
        //
        ViewAssoc assoc = mf.newViewAssoc(dmx.getAssoc(assocId).getModel(), viewProps);
        me.addAssocToTopicmap(topicmapId, assoc);
    }

    // --- Viewmodel Customizers ---

    private void invokeViewmodelCustomizers(RelatedTopic topic, ViewProps viewProps) {
        for (ViewmodelCustomizer customizer : viewmodelCustomizers) {
            invokeViewmodelCustomizer(customizer, topic, viewProps);
        }
    }

    private void invokeViewmodelCustomizer(ViewmodelCustomizer customizer, RelatedTopic topic,
                                                                           ViewProps viewProps) {
        try {
            customizer.enrichViewProps(topic, viewProps);
        } catch (Exception e) {
            throw new RuntimeException("Invoking viewmodel customizer for topic " + topic.getId() + " failed, " +
                "customizer=\"" + customizer.getClass().getName() + "\"", e);
        }
    }

    // --- Topicmap Types ---

    private TopicmapType getTopicmapType(String topicmapTypeUri) {
        TopicmapType topicmapType = topicmapTypes.get(topicmapTypeUri);
        if (topicmapType == null) {
            throw new RuntimeException("Topicmap type \"" + topicmapTypeUri + "\" not registered");
        }
        return topicmapType;
    }

    // ---

    private InputStream invokeWebclient() {
        return dmx.getPlugin("systems.dmx.webclient").getStaticResource("/web/index.html");
    }
}
