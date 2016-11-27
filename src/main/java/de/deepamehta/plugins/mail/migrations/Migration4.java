package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;
import java.util.logging.Logger;

public class Migration4 extends Migration {

    private static final Logger log = Logger.getLogger(Migration4.class.getName());

    @Inject
    private WorkspacesService workspaces; // used in Migration3

    @Override
    public void run() {
        // 1) Moves configuration topic into the "Administration" workspace
        Topic pluginConfig = dm4.getTopicByUri("dm4.mail.config");
        workspaces.assignToWorkspace(pluginConfig, dm4.getAccessControl().getAdministrationWorkspaceId());
    }

}
