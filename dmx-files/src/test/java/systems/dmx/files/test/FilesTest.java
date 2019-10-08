package systems.dmx.files.test;

import systems.dmx.files.DirectoryListing;
import systems.dmx.files.DirectoryListing.FileItem;
import systems.dmx.files.ItemKind;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import org.codehaus.jettison.json.JSONException;

import java.io.File;
import java.util.List;



public class FilesTest {

    // ### FIXME: enable the tests
    // ### They rely on the dmx.filerepo.path system property as defined in project POM

    @Ignore @Test
    public void directoryListing() {
        DirectoryListing dir = new DirectoryListing(new File("/"), null);       // pathMapper=null
        List<FileItem> items = dir.getFileItems();
        FileItem item = items.get(0);
        ItemKind kind = item.getItemKind();
        assertTrue(kind == ItemKind.FILE || kind == ItemKind.DIRECTORY);
    }

    @Ignore @Test
    public void directoryListingJSON() {
        try {
            DirectoryListing dir = new DirectoryListing(new File("/"), null);   // pathMapper=null
            List<FileItem> items = dir.getFileItems();
            FileItem item = items.get(0);
            String kind = item.toJSON().getString("kind");
            assertTrue(kind.equals("file") || kind.equals("directory"));
        } catch (JSONException e) {
            fail();
        }
    }
}
