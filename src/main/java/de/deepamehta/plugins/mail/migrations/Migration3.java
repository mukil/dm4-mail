package de.deepamehta.plugins.mail.migrations;

import de.deepamehta.core.service.Migration;
import java.util.logging.Logger;

public class Migration3 extends Migration {

    private static final Logger log = Logger.getLogger(Migration3.class.getName());

    @Override
    public void run() {
        // 1) ### Turn to new config service
        dm4.getTopicType("dm4.accesscontrol.user_account")//
                .addAssocDef(mf.newAssociationDefinitionModel("dm4.core.composition_def",//
                        "dm4.accesscontrol.user_account", "dm4.mail.from",//
                        "dm4.core.many", "dm4.core.one"));
        // 2) configure acls of configuration topic
        // for now and a lack of a collaborative mail concept we do nothing
        // and leave mail configuration in 4.8 editable by no one
        // normally it would be "Administration" but then sending of mails might break (see #814)
    }

}
