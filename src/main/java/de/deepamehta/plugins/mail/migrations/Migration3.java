package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;
import java.util.logging.Logger;

public class Migration3 extends Migration {

    private static final Logger log = Logger.getLogger(Migration3.class.getName());

    @Inject
    private WorkspacesService workspaces; // used in Migration3

    @Override
    public void run() {
        // 1) Enriches Type Definition of a "User Account" about a "Mail From" (dummy) field
        dm4.getTopicType("dm4.accesscontrol.user_account")//
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.composition_def",//
                        "dm4.accesscontrol.user_account", "dm4.mail.from",//
                        "dm4.core.many", "dm4.core.one"));
        // 2) Moves configuration topic into the "System" workspace
        Topic pluginConfig = dm4.getTopicByUri("dm4.mail.config");
        workspaces.assignToWorkspace(pluginConfig, dm4.getAccessControl().getSystemWorkspaceId());
    }

}
