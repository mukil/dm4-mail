package de.deepamehta.plugins.mail.service;

import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.PluginService;

public interface MailService extends PluginService {

    public ResultSet<Topic> getSearchParentTypes();

}
