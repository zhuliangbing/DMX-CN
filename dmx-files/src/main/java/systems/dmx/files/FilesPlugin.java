package systems.dmx.files;

import systems.dmx.files.event.CheckDiskQuota;
import systems.dmx.config.ConfigDefinition;
import systems.dmx.config.ConfigModificationRole;
import systems.dmx.config.ConfigService;
import systems.dmx.config.ConfigTarget;

import systems.dmx.core.Assoc;
import systems.dmx.core.DMXObject;
import systems.dmx.core.Topic;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.SimpleValue;
import systems.dmx.core.model.TopicModel;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Cookies;
import systems.dmx.core.service.DMXEvent;
import systems.dmx.core.service.EventListener;
import systems.dmx.core.service.Inject;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.service.accesscontrol.Operation;
import systems.dmx.core.service.accesscontrol.PrivilegedAccess;
import systems.dmx.core.service.event.StaticResourceFilter;
import systems.dmx.core.util.DMXUtils;
import systems.dmx.core.util.JavaUtils;

import org.apache.commons.io.IOUtils;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.Desktop;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



@Path("/files")
@Produces("application/json")
public class FilesPlugin extends PluginActivator implements FilesService, StaticResourceFilter, PathMapper {

    // ------------------------------------------------------------------------------------------------------- Constants

    public static final String FILE_REPOSITORY_PATH = System.getProperty("dmx.filerepo.path", "/");
    public static final boolean FILE_REPOSITORY_PER_WORKSPACE = Boolean.getBoolean("dmx.filerepo.per_workspace");
    public static final int DISK_QUOTA_MB = Integer.getInteger("dmx.filerepo.disk_quota", -1);
    // Note: the default values are required in case no config file is in effect. This applies when DM is started
    // via feature:install from Karaf. The default value must match the value defined in project POM.

    private static final String FILE_REPOSITORY_URI = "/filerepo";

    private static final String WORKSPACE_DIRECTORY_PREFIX = "/workspace-";
    private static final Pattern PER_WORKSPACE_PATH_PATTERN = Pattern.compile(WORKSPACE_DIRECTORY_PREFIX + "(\\d+).*");

    // Events
    public static DMXEvent CHECK_DISK_QUOTA = new DMXEvent(CheckDiskQuota.class) {
        @Override
        public void dispatch(EventListener listener, Object... params) {
            ((CheckDiskQuota) listener).checkDiskQuota(
                (String) params[0], (Long) params[1], (Long) params[2]
            );
        }
    };

    // ---------------------------------------------------------------------------------------------- Instance Variables

    @Inject
    private ConfigService configService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ***********************************
    // *** FilesService Implementation ***
    // ***********************************



    // === File System Representation ===

    @GET
    @Path("/file/{path}")
    @Transactional
    @Override
    public Topic getFileTopic(@PathParam("path") String repoPath) {
        String operation = "Creating File topic for repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            // 1) pre-checks
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            //
            // 2) check if topic already exists
            Topic fileTopic = fetchFileTopic(repoPath(file));
            if (fileTopic != null) {
                logger.info(operation + " SKIPPED -- already exists");
                return fileTopic.loadChildTopics();
            }
            // 3) create topic
            return createFileTopic(file);
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    @GET
    @Path("/folder/{path}")
    @Transactional
    @Override
    public Topic getFolderTopic(@PathParam("path") String repoPath) {
        String operation = "Creating Folder topic for repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            // 1) pre-checks
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            //
            // 2) check if topic already exists
            Topic folderTopic = fetchFolderTopic(repoPath(file));
            if (folderTopic != null) {
                logger.info(operation + " SKIPPED -- already exists");
                return folderTopic.loadChildTopics();
            }
            // 3) create topic
            return createFolderTopic(file);
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // ---

    @GET
    @Path("/parent/{id}/file/{path}")
    @Transactional
    @Override
    public Topic getChildFileTopic(@PathParam("id") long folderTopicId, @PathParam("path") String repoPath) {
        Topic topic = getFileTopic(repoPath);
        createFolderAssoc(folderTopicId, topic);
        return topic;
    }

    @GET
    @Path("/parent/{id}/folder/{path}")
    @Transactional
    @Override
    public Topic getChildFolderTopic(@PathParam("id") long folderTopicId, @PathParam("path") String repoPath) {
        Topic topic = getFolderTopic(repoPath);
        createFolderAssoc(folderTopicId, topic);
        return topic;
    }



    // === File Repository ===

    @POST
    @Path("/{path}")
    @Consumes("multipart/form-data")
    @Transactional
    @Override
    public StoredFile storeFile(UploadedFile file, @PathParam("path") String repoPath) {
        String operation = "Storing " + file + " at repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            // 1) pre-checks
            File directory = absolutePath(repoPath);    // throws FileRepositoryException
            checkExistence(directory);                  // throws FileRepositoryException
            //
            // 2) store file
            File repoFile = unusedPath(directory, file);
            file.write(repoFile);
            //
            // 3) create topic
            Topic fileTopic = createFileTopic(repoFile);
            return new StoredFile(repoFile.getName(), repoPath(fileTopic), fileTopic.getId());
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // Note: this is not a resource method. So we don't throw a WebApplicationException here.
    @Override
    public Topic createFile(InputStream in, String repoPath) {
        String operation = "Creating file (from input stream) at repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            // 1) pre-checks
            File file = absolutePath(repoPath);         // throws FileRepositoryException
            //
            // 2) store file
            FileOutputStream out = new FileOutputStream(file);
            IOUtils.copy(in, out);
            in.close();
            out.close();
            //
            // 3) create topic
            // ### TODO: think about overwriting an existing file.
            // ### FIXME: in this case the existing file topic is not updated and might reflect e.g. the wrong size.
            return getFileTopic(repoPath);
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    @POST
    @Path("/{path}/folder/{folder_name}")
    @Override
    public void createFolder(@PathParam("folder_name") String folderName, @PathParam("path") String repoPath) {
        String operation = "Creating folder \"" + folderName + "\" at repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            // 1) pre-checks
            File directory = absolutePath(repoPath);    // throws FileRepositoryException
            checkExistence(directory);                  // throws FileRepositoryException
            //
            // 2) create directory
            File repoFile = path(directory, folderName);
            if (repoFile.exists()) {
                throw new RuntimeException("File or directory \"" + repoFile + "\" already exists");
            }
            //
            boolean success = repoFile.mkdir();
            //
            if (!success) {
                throw new RuntimeException("File.mkdir() failed (file=\"" + repoFile + "\")");
            }
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // ---

    @GET
    @Path("/{path}/info")
    @Override
    public ResourceInfo getResourceInfo(@PathParam("path") String repoPath) {
        String operation = "Getting resource info for repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            //
            return new ResourceInfo(file);
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    @GET
    @Path("/{path}")
    @Override
    public DirectoryListing getDirectoryListing(@PathParam("path") String repoPath) {
        String operation = "Getting directory listing for repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            File directory = absolutePath(repoPath);    // throws FileRepositoryException
            checkExistence(directory);                  // throws FileRepositoryException
            //
            return new DirectoryListing(directory, this);
            // ### TODO: if directory is no directory send NOT FOUND
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    @Override
    public String getRepositoryPath(URL url) {
        String operation = "Checking for file repository URL (\"" + url + "\")";
        try {
            if (!DMXUtils.isDMXURL(url)) {
                logger.info(operation + " => null");
                return null;
            }
            //
            String path = url.getPath();
            if (!path.startsWith(FILE_REPOSITORY_URI)) {
                logger.info(operation + " => null");
                return null;
            }
            // ### TODO: compare to repoPath(HttpServletRequest request) in both regards, cutting off + 1, and decoding
            String repoPath = path.substring(FILE_REPOSITORY_URI.length());
            logger.info(operation + " => \"" + repoPath + "\"");
            return repoPath;
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // ---

    // Note: this is not a resource method. So we don't throw a WebApplicationException here.
    // To access a file remotely use the /filerepo resource.
    @Override
    public File getFile(String repoPath) {
        String operation = "Accessing the file/directory at repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            return file;
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // Note: this is not a resource method. So we don't throw a WebApplicationException here.
    // To access a file remotely use the /filerepo resource.
    @Override
    public File getFile(long fileTopicId) {
        String operation = "Accessing the file/directory of File/Folder topic " + fileTopicId;
        try {
            logger.info(operation);
            //
            String repoPath = repoPath(fileTopicId);
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            return file;
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // ---

    @Override
    public boolean fileExists(String repoPath) {
        String operation = "Checking existence of file/directory at repository path \"" + repoPath + "\"";
        try {
            logger.info(operation);
            //
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            return file.exists();
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    // ---

    @Override
    public String pathPrefix() {
        String operation = "Constructing the repository path prefix";
        try {
            return FILE_REPOSITORY_PER_WORKSPACE ? _pathPrefix(getWorkspaceId()) : "";
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }

    @Override
    public String pathPrefix(long workspaceId) {
        return FILE_REPOSITORY_PER_WORKSPACE ? _pathPrefix(workspaceId) : "";
    }

    // ---

    @GET
    @Path("/open/{id}")
    @Override
    public int openFile(@PathParam("id") long fileTopicId) {
        String operation = "Opening the file of File topic " + fileTopicId;
        try {
            logger.info(operation);
            //
            String repoPath = repoPath(fileTopicId);
            File file = absolutePath(repoPath);     // throws FileRepositoryException
            checkExistence(file);                   // throws FileRepositoryException
            //
            logger.info("### Opening file \"" + file + "\"");
            Desktop.getDesktop().open(file);
            //
            // Note: a HTTP GET method MUST return a non-void type
            return 0;
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(new RuntimeException(operation + " failed", e), e.getStatus());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void preInstall() {
        configService.registerConfigDefinition(new ConfigDefinition(
            ConfigTarget.TYPE_INSTANCES, "dmx.accesscontrol.username",
            mf.newTopicModel("dmx.files.disk_quota", new SimpleValue(DISK_QUOTA_MB)),
            ConfigModificationRole.ADMIN
        ));
    }

    @Override
    public void init() {
        publishFileSystem(FILE_REPOSITORY_URI, FILE_REPOSITORY_PATH);
    }

    @Override
    public void shutdown() {
        // Note 1: unregistering is crucial e.g. for redeploying the Files plugin. The next register call
        // (at preInstall() time) would fail as the Config service already holds such a registration.
        // Note 2: we must check if the Config service is still available. If the Config plugin is redeployed the
        // Files plugin is stopped/started as well but at shutdown() time the Config service is already gone.
        if (configService != null) {
            configService.unregisterConfigDefinition("dmx.files.disk_quota");
        } else {
            logger.warning("Config service is already gone");
        }
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void staticResourceFilter(HttpServletRequest request, HttpServletResponse response) {
        try {
            String repoPath = repoPath(request);    // Note: the path is not canonized
            if (repoPath != null) {
                logger.fine("### Checking access to repository path \"" + repoPath + "\"");
                File path = absolutePath(repoPath);             // throws FileRepositoryException 403 Forbidden
                checkExistence(path);                           // throws FileRepositoryException 404 Not Found
                checkAuthorization(repoPath(path), request);    // throws FileRepositoryException 401 Unauthorized
                //
                // prepare downloading
                if (request.getParameter("download") != null) {
                    logger.info("### Downloading file \"" + path + "\"");
                    response.setHeader("Content-Disposition", "attachment;filename=" + path.getName());
                }
            }
        } catch (FileRepositoryException e) {
            throw new WebApplicationException(e, e.getStatus());
        }
    }



    // *********************************
    // *** PathMapper Implementation ***
    // *********************************



    @Override
    public String repoPath(File path) {
        try {
            String repoPath = path.getPath();
            //
            if (!repoPath.startsWith(FILE_REPOSITORY_PATH)) {
                throw new RuntimeException("Absolute path \"" + path + "\" is not a repository path");
            }
            // The repository path is calculated by removing the repository base path from the absolute path.
            // Because the base path never ends with a slash the calculated repo path will always begin with a slash
            // (it is never removed). There is one exception: the base path *does* end with a slash if it represents
            // the entire file system, that is "/". In that case it must *not* be removed from the absolute path.
            // In that case the repository path is the same as the absolute path.
            if (!FILE_REPOSITORY_PATH.equals("/")) {
                repoPath = repoPath.substring(FILE_REPOSITORY_PATH.length());
                if (repoPath.equals("")) {
                    repoPath = "/";
                }
            }
            // ### FIXME: Windows drive letter?
            return repoPath;
        } catch (Exception e) {
            throw new RuntimeException("Mapping absolute path \"" + path + "\" to a repository path failed", e);
        }
    }

    // ------------------------------------------------------------------------------------------------- Private Methods



    // === File System Representation ===

    /**
     * Fetches the File topic representing the file at the given repository path.
     * If no such File topic exists <code>null</code> is returned.
     *
     * @param   repoPath        A repository path. Must be canonic.
     */
    private Topic fetchFileTopic(String repoPath) {
        return fetchFileOrFolderTopic(repoPath, "dmx.files.file");
    }

    /**
     * Fetches the Folder topic representing the folder at the given repository path.
     * If no such Folder topic exists <code>null</code> is returned.
     *
     * @param   repoPath        A repository path. Must be canonic.
     */
    private Topic fetchFolderTopic(String repoPath) {
        return fetchFileOrFolderTopic(repoPath, "dmx.files.folder");
    }

    // ---

    /**
     * Fetches the File/Folder topic representing the file/directory at the given repository path.
     * If no such File/Folder topic exists <code>null</code> is returned.
     *
     * @param   repoPath        A repository path. Must be canonic.
     * @param   topicTypeUri    The type of the topic to fetch: either "dmx.files.file" or "dmx.files.folder".
     */
    private Topic fetchFileOrFolderTopic(String repoPath, String topicTypeUri) {
        Topic pathTopic = fetchPathTopic(repoPath);
        if (pathTopic != null) {
            return pathTopic.getRelatedTopic("dmx.core.composition", "dmx.core.child", "dmx.core.parent", topicTypeUri);
        }
        return null;
    }

    /**
     * @param   repoPath        A repository path. Must be canonic.
     */
    private Topic fetchPathTopic(String repoPath) {
        return dmx.getTopicByValue("dmx.files.path", new SimpleValue(repoPath));
    }

    // ---

    /**
     * Creates a File topic representing the file at the given absolute path.
     *
     * @param   path    A canonic absolute path.
     *
     * @return  The created File topic.
     */
    private Topic createFileTopic(File path) throws Exception {
        ChildTopicsModel childTopics = mf.newChildTopicsModel()
            .put("dmx.files.file_name", path.getName())
            .put("dmx.files.path", repoPath(path))  // TODO: is repo path already known by caller? Pass it?
            .put("dmx.files.size", path.length());
        //
        String mediaType = JavaUtils.getFileType(path.getName());
        if (mediaType != null) {
            childTopics.put("dmx.files.media_type", mediaType);
        }
        //
        return createFileOrFolderTopic(mf.newTopicModel("dmx.files.file", childTopics));      // throws Exception
    }

    /**
     * Creates a Folder topic representing the directory at the given absolute path.
     *
     * @param   path    A canonic absolute path.
     */
    private Topic createFolderTopic(File path) throws Exception {
        String folderName = null;
        String repoPath = repoPath(path);   // Note: repo path is already calculated by caller. Could be passed.
        File repoPathFile = new File(repoPath);
        //
        // if the repo path represents a workspace root directory the workspace name is used as Folder Name
        if (FILE_REPOSITORY_PER_WORKSPACE) {
            if (repoPathFile.getParent().equals("/")) {
                String workspaceName = dmx.getTopic(getWorkspaceId(repoPath)).getSimpleValue().toString();
                folderName = workspaceName;
            }
        }
        // by default the directory name is used as Folder Name
        if (folderName == null) {
            folderName = repoPathFile.getName();    // Note: getName() of "/" returns ""
        }
        //
        return createFileOrFolderTopic(mf.newTopicModel("dmx.files.folder", mf.newChildTopicsModel()
            .put("dmx.files.folder_name", folderName)
            .put("dmx.files.path", repoPath)));     // throws Exception
    }

    // ---

    /**
     * @param   repoPath        A repository path. Must be canonic.
     */
    private Topic createFileOrFolderTopic(final TopicModel model) throws Exception {
        // We suppress standard workspace assignment here as File and Folder topics require a special assignment
        // Note: runWithoutWorkspaceAssignment() throws Exception
        Topic topic = dmx.getPrivilegedAccess().runWithoutWorkspaceAssignment(new Callable<Topic>() {
            @Override
            public Topic call() {
                return dmx.createTopic(model);
            }
        });
        createWorkspaceAssignment(topic, repoPath(topic));
        return topic;
    }

    /**
     * @param   topic   a File topic, or a Folder topic.
     */
    private void createFolderAssoc(final long folderTopicId, Topic topic) {
        try {
            final long topicId = topic.getId();
            boolean exists = dmx.getAssocs(folderTopicId, topicId, "dmx.core.composition").size() > 0;
            if (!exists) {
                // We suppress standard workspace assignment as the folder association requires a special assignment
                Assoc assoc = dmx.getPrivilegedAccess().runWithoutWorkspaceAssignment(new Callable<Assoc>() {
                    @Override
                    public Assoc call() {
                        return dmx.createAssoc(mf.newAssocModel("dmx.core.composition",
                            mf.newTopicPlayerModel(folderTopicId, "dmx.core.parent"),
                            mf.newTopicPlayerModel(topicId,       "dmx.core.child")
                        ));
                    }
                });
                createWorkspaceAssignment(assoc, repoPath(topic));
            }
        } catch (Exception e) {
            throw new RuntimeException("Creating association to Folder topic " + folderTopicId + " failed", e);
        }
    }

    // ---

    /**
     * Creates a workspace assignment for a File topic, a Folder topic, or a folder association (type "Aggregation").
     * The workspce is calculated from both, the "dmx.filerepo.per_workspace" flag and the given repository path.
     *
     * @param   object  a File topic, a Folder topic, or a folder association (type "Aggregation").
     */
    private void createWorkspaceAssignment(DMXObject object, String repoPath) {
        try {
            PrivilegedAccess pa = dmx.getPrivilegedAccess();
            long workspaceId = FILE_REPOSITORY_PER_WORKSPACE ? getWorkspaceId(repoPath) : pa.getDMXWorkspaceId();
            pa.assignToWorkspace(object, workspaceId);
        } catch (Exception e) {
            throw new RuntimeException("Creating workspace assignment for File/Folder topic or folder association " +
                "failed", e);
        }
    }



    // === File Repository ===

    /**
     * Maps a repository path to an absolute path.
     * <p>
     * Checks the repository path to fight directory traversal attacks.
     *
     * @param   repoPath    A repository path. Relative to the repository base path.
     *                      Must begin with slash, no slash at the end.
     *
     * @return  The canonized absolute path.
     */
    private File absolutePath(String repoPath) throws FileRepositoryException {
        try {
            File repo = new File(FILE_REPOSITORY_PATH);
            //
            if (!repo.exists()) {
                throw new RuntimeException("File repository \"" + repo + "\" does not exist");
            }
            //
            String _repoPath = repoPath;
            if (FILE_REPOSITORY_PER_WORKSPACE) {
                String pathPrefix;
                if (repoPath.equals("/")) {
                    pathPrefix = _pathPrefix(getWorkspaceId());
                    _repoPath = pathPrefix;
                } else {
                    pathPrefix = _pathPrefix(getWorkspaceId(repoPath));
                }
                createWorkspaceFileRepository(new File(repo, pathPrefix));
            }
            //
            repo = new File(repo, _repoPath);
            //
            return checkPath(repo);         // throws FileRepositoryException 403 Forbidden
        } catch (FileRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Mapping repository path \"" + repoPath + "\" to an absolute path failed", e);
        }
    }

    // ---

    /**
     * Checks if the absolute path represents a directory traversal attack.
     * If so a FileRepositoryException (403 Forbidden) is thrown.
     *
     * @param   path    The absolute path to check.
     *
     * @return  The canonized absolute path.
     */
    private File checkPath(File path) throws FileRepositoryException, IOException {
        // Note: a directory path returned by getCanonicalPath() never contains a "/" at the end.
        // Thats why "dmx.filerepo.path" is expected to have no "/" at the end as well.
        path = path.getCanonicalFile();     // throws IOException
        boolean pointsToRepository = path.getPath().startsWith(FILE_REPOSITORY_PATH);
        //
        logger.fine("Checking path \"" + path + "\"\n  dmx.filerepo.path=" +
            "\"" + FILE_REPOSITORY_PATH + "\" => " + (pointsToRepository ? "PATH OK" : "FORBIDDEN"));
        //
        if (!pointsToRepository) {
            throw new FileRepositoryException("\"" + path + "\" does not point to file repository", Status.FORBIDDEN);
        }
        //
        return path;
    }

    private void checkExistence(File path) throws FileRepositoryException {
        boolean exists = path.exists();
        //
        logger.fine("Checking existence of \"" + path + "\" => " + (exists ? "EXISTS" : "NOT FOUND"));
        //
        if (!exists) {
            throw new FileRepositoryException("File or directory \"" + path + "\" does not exist", Status.NOT_FOUND);
        }
    }

    /**
     * Checks if the user associated with a request is authorized to access a repository file.
     * If not authorized a FileRepositoryException (401 Unauthorized) is thrown.
     *
     * @param   repoPath    The repository path of the file to check. Must be canonic.
     * @param   request     The request.
     */
    private void checkAuthorization(String repoPath, HttpServletRequest request) throws FileRepositoryException {
        try {
            if (FILE_REPOSITORY_PER_WORKSPACE) {
                // We check authorization for the repository path by checking access to the corresponding File topic.
                Topic fileTopic = fetchFileTopic(repoPath);
                if (fileTopic != null) {
                    // We must perform access control for the fetchFileTopic() call manually here.
                    //
                    // Although the AccessControlPlugin's CheckTopicReadAccess kicks in, the request is *not* injected
                    // into the AccessControlPlugin letting fetchFileTopic() effectively run as "System".
                    //
                    // Note: checkAuthorization() is called (indirectly) from an OSGi HTTP service static resource
                    // HttpContext. JAX-RS is not involved here. That's why no JAX-RS injection takes place.
                    String username = dmx.getPrivilegedAccess().getUsername(request);
                    long fileTopicId = fileTopic.getId();
                    if (!dmx.getPrivilegedAccess().hasPermission(username, Operation.READ, fileTopicId)) {
                        throw new FileRepositoryException(userInfo(username) + " has no READ permission for " +
                            "repository path \"" + repoPath + "\" (File topic ID=" + fileTopicId + ")",
                            Status.UNAUTHORIZED);
                    }
                } else {
                    throw new RuntimeException("Missing File topic for repository path \"" + repoPath + "\"");
                }
            }
        } catch (FileRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Checking authorization for repository path \"" + repoPath + "\" failed", e);
        }
    }

    private String userInfo(String username) {
        return "user " + (username != null ? "\"" + username + "\"" : "<anonymous>");
    }

    // ---

    /**
     * Constructs an absolute path from an absolute path and a file name.
     *
     * @param   directory   An absolute path.
     *
     * @return  The constructed absolute path.
     */
    private File path(File directory, String fileName) {
        return new File(directory, fileName);
    }

    /**
     * Constructs an absolute path for storing an uploaded file.
     * If a file with that name already exists in the specified directory it remains untouched and the uploaded file
     * is stored with a unique name (by adding a number).
     *
     * @param   directory   The directory to store the uploaded file to.
     *                      A canonic absolute path.
     *
     * @return  The canonized absolute path.
     */
    private File unusedPath(File directory, UploadedFile file) {
        return JavaUtils.findUnusedFile(path(directory, file.getName()));
    }

    // ---

    // Note: there is also a public repoPath() method (part of the PathMapper API).
    // It maps an absolute path to a repository path.

    /**
     * Returns the repository path of a File/Folder topic.
     *
     * @return  The repository path, is canonic.
     */
    private String repoPath(long fileTopicId) {
        return repoPath(dmx.getTopic(fileTopicId));
    }

    /**
     * Returns the repository path of a File/Folder topic.
     *
     * @return  The repository path, is canonic.
     */
    private String repoPath(Topic topic) {
        return topic.getChildTopics().getString("dmx.files.path");
    }

    /**
     * Returns the repository path of a filerepo request.
     *
     * @return  The repository path or <code>null</code> if the request is not a filerepo request.
     *          Note: the returned path is <i>not</i> canonized.
     */
    private String repoPath(HttpServletRequest request) {
        String repoPath = null;
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith(FILE_REPOSITORY_URI)) {
            // Note: the request URI is e.g. /filerepo/%2Fworkspace-1821%2Flogo-escp-europe.gif
            // +1 cuts off the slash following /filerepo
            repoPath = requestURI.substring(FILE_REPOSITORY_URI.length() + 1);
            repoPath = JavaUtils.decodeURIComponent(repoPath);
        }
        return repoPath;
    }

    // --- Per-workspace file repositories ---

    private void createWorkspaceFileRepository(File repo) {
        try {
            if (!repo.exists()) {
                if (repo.mkdir()) {
                    logger.info("### Per-workspace file repository created: \"" + repo + "\"");
                } else {
                    throw new RuntimeException("Directory \"" + repo + "\" not created successfully");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Creating per-workspace file repository failed", e);
        }
    }

    // ---

    private long getWorkspaceId() {
        Cookies cookies = Cookies.get();
        if (!cookies.has("dmx_workspace_id")) {
            throw new RuntimeException("If \"dmx.filerepo.per_workspace\" is set the request requires a " +
                "\"dmx_workspace_id\" cookie");
        }
        return cookies.getLong("dmx_workspace_id");
    }

    private long getWorkspaceId(String repoPath) {
        Matcher m = PER_WORKSPACE_PATH_PATTERN.matcher(repoPath);
        if (!m.matches()) {
            throw new RuntimeException("No workspace recognized in repository path \"" + repoPath + "\"");
        }
        long workspaceId = Long.parseLong(m.group(1));
        return workspaceId;
    }

    // ---

    private String _pathPrefix(long workspaceId) {
        return WORKSPACE_DIRECTORY_PREFIX + workspaceId;
    }
}
