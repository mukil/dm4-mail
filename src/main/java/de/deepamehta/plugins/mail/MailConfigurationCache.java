package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.CoreService;
import java.util.List;

/**
 * Reveals and caches mail configuration parts.
 * 
 * @TODO configure MTA user and password (requires a password data type)
 */
class MailConfigurationCache {

    private static Logger log = Logger.getLogger(MailConfigurationCache.class.getName());

    public static final String MAIL_CONFIG = "dm4.mail.config";
    public static final String SMTP_HOST = "dm4.mail.config.host";

    private Topic config = null;
    private final CoreService dms;
    private RecipientType defaultRecipientType = null;
    private RelatedTopic defaultSender = null;
    private boolean defaultSenderIsNull = false;
    private List<Topic> recipientTypes;
    private Set<String> recipientTypeUris;
    private Map<String, Topic> searchParentTypes;
    private List<RelatedTopic> searchTypes;
    private Set<String> searchTypeUris;
    private String smtpHost = null;

    public MailConfigurationCache(CoreService dms) {
        this.dms = dms;
    }

    /**
     * Returns the corresponding enumeration value or the configured default
     * recipient type.
     * 
     * @param type
     *            Valid recipient type enumeration value.
     * @return Recipient type.
     */
    public RecipientType checkRecipientType(String type) {
        if (type == null || type.isEmpty()// type URI is unknown?
                || getRecipientTypeUris().contains(type) == false) {
            log.fine("Check recipient type");
            return getDefaultRecipientType();
        } else {
            return RecipientType.fromUri(type);
        }
    }

    private Topic getConfiguration() {
        if (config == null) {
            log.fine("Access mail plugin configuration");
            config = dms.getTopicByUri(MAIL_CONFIG).loadChildTopics();
        }
        return config;
    }

    public Topic getTopic() {
        return getConfiguration();
    }

    public RecipientType getDefaultRecipientType() {
        if (defaultRecipientType == null) {
            log.fine("Get default recipient type");
            Topic type = getConfiguration().getChildTopics().getTopic(RECIPIENT_TYPE);
            defaultRecipientType = RecipientType.fromUri(type.getUri());
        }
        return defaultRecipientType;
    }

    public RelatedTopic getDefaultSender() {
        if (defaultSenderIsNull == false && defaultSender == null) {
            log.fine("Get default sender");
            defaultSender = getConfiguration().getRelatedTopic(SENDER, PARENT, CHILD, null);
            if (defaultSender == null) {
                defaultSenderIsNull = true;
            }
        }
        return defaultSender;
    }

    public List<Topic> getRecipientTypes() {
        if (recipientTypes == null) {
            log.fine("Get recipient types");
            recipientTypes = dms.getTopicsByType(RECIPIENT_TYPE);
        }
        return recipientTypes;
    }

    public Set<String> getRecipientTypeUris() {
        if (recipientTypeUris == null) {
            log.fine("Get recipient type URIs");
            recipientTypeUris = new HashSet<String>();
            for (Topic topic : getRecipientTypes()) {
                recipientTypeUris.add(topic.getUri());
            }
        }
        return recipientTypeUris;
    }

    public Collection<Topic> getSearchParentTypes() {
        return revealSearchParentTypes().values();
    }

    public List<RelatedTopic> getSearchTypes() {
        if (searchTypes == null) {
            log.fine("Get search types");
            // get aggregated composite search types
            // FIXME use a specific association type and field renderer
            searchTypes = getConfiguration().getRelatedTopics(AGGREGATION, PARENT, CHILD, TOPIC_TYPE);
        }
        return searchTypes;
    }

    public Set<String> getSearchTypeUris() {
        if (searchTypeUris == null) {
            log.fine("Get search type URIs");
            searchTypeUris = new LinkedHashSet<String>();
            for (Topic topic : getSearchTypes()) {
                searchTypeUris.add(topic.getUri());
            }
        }
        return searchTypeUris;
    }

    public String getSmtpHost() {
        if (smtpHost == null) {
            log.fine("Get SMTP host");
            smtpHost = getConfiguration().getChildTopics()//
                    .getTopic(SMTP_HOST).getSimpleValue().toString();
        }
        return smtpHost;
    }

    private Map<String, Topic> revealSearchParentTypes() {
        if (searchParentTypes == null) {
            log.fine("Get search parent types");
            searchParentTypes = new HashMap<String, Topic>();
            for (Topic type : getSearchTypes()) {
                searchParentTypes.put(type.getUri(), type.getRelatedTopic(null,//
                        CHILD_TYPE, PARENT_TYPE, TOPIC_TYPE));
            }
        }
        return searchParentTypes;
    }

}
