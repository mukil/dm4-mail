package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.service.Migration;
import java.util.logging.Logger;

public class Migration3 extends Migration {

    private static final Logger log = Logger.getLogger(Migration3.class.getName());

    @Override
    public void run() {
        // 1) ### Turn to new config service
        dms.getTopicType("dm4.accesscontrol.user_account")//
                .addAssocDef(new AssociationDefinitionModel("dm4.core.composition_def",//
                        "dm4.accesscontrol.user_account", "dm4.mail.from",//
                        "dm4.core.many", "dm4.core.one"));
        // 2) configure acls of configuration topic
        // (for now and a lack of a collaborative mail concept we do nothing
        // and leave mail configuration in 4.6 editable by everyone a.k.a. "public")
        // normally it would be "System" but then sending mails would be broken (see #814)
    }

}
