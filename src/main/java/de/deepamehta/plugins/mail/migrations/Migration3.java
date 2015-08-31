package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.mail.MailPlugin;
import java.io.File;
import java.util.logging.Logger;
import javax.ws.rs.WebApplicationException;
import org.codehaus.jettison.json.JSONException;

public class Migration3 extends Migration {

    private static final Logger log = Logger.getLogger(Migration3.class.getName());

    @Inject
    FilesService fileService;

    @Override
    public void run() {
        // 1)
        createAttachmentDirectory();
        // 2)
        dms.getTopicType("dm4.accesscontrol.user_account")//
                .addAssocDef(new AssociationDefinitionModel("dm4.core.composition_def",//
                        "dm4.accesscontrol.user_account", "dm4.mail.from",//
                        "dm4.core.many", "dm4.core.one"));
        // 3) configure acls of configuration topic
        // (for now and a lack of a collaborative mail concept we do nothing
        // and leave mail configuration in 4.6 editable by everyone a.k.a. "public")
        // normally it would be "System" but then sending mails would be broken (see #814)
    }

    private void createAttachmentDirectory() {
        try {
            ResourceInfo resourceInfo = fileService.getResourceInfo(MailPlugin.ATTACHMENTS);
            String kind = resourceInfo.toJSON().getString("kind");
            if (kind.equals("directory") == false) {
                String repoPath = System.getProperty("dm4.filerepo.path");
                String message = "Migration 3: attachment storage directory " + repoPath + File.separator + MailPlugin.ATTACHMENTS
                        + " can not be used";
                throw new IllegalStateException(message);
            }
        } catch (WebApplicationException e) { // !exists
            // catch fileService info request error => create directory
            if (e.getResponse().getStatus() != 404) {
                throw e;
            } else {
                log.info("Migration 3: create attachment directory");
                fileService.createFolder(MailPlugin.ATTACHMENTS, "/");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
