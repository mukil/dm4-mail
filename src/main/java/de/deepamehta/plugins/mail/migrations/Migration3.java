package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.service.Migration;

public class Migration3 extends Migration {

    @Override
    public void run() {
        dms.getTopicType("dm4.accesscontrol.user_account", null)//
                .addAssocDef(new AssociationDefinitionModel("dm4.core.composition_def",//
                        "dm4.accesscontrol.user_account", "dm4.mail.from",//
                        "dm4.core.many", "dm4.core.one"));
    }

}
