package systems.dmx.files;

import systems.dmx.core.JSONEnabled;

import org.codehaus.jettison.json.JSONObject;



public class StoredFile implements JSONEnabled {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private final String fileName;
    private final String repoPath;
    private final long fileTopicId;

    // ---------------------------------------------------------------------------------------------------- Constructors

    StoredFile(String fileName, String repoPath, long fileTopicId) {
        this.fileName = fileName;
        this.repoPath = repoPath;
        this.fileTopicId = fileTopicId;
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public String getFileName() {
        return fileName;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public long getFileTopicId() {
        return fileTopicId;
    }

    // ---

    @Override
    public JSONObject toJSON() {
        try {
            return new JSONObject()
                .put("fileName", fileName)
                .put("repoPath", repoPath)
                .put("topicId", fileTopicId);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
