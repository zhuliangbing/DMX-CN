package systems.dmx.files;

import java.io.File;



interface PathMapper {

    /**
     * Maps an absolute path to a repository path.
     *
     * @param   path    A canonic absolute path.
     *
     * @return  A repository path. Relative to the repository base path.
     *          Begins with slash, no slash at the end.
     */
    String repoPath(File path);
}
