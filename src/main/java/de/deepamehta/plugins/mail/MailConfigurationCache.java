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
import static de.deepamehta.plugins.mail.service.MailService.AGGREGATION;
import static de.deepamehta.plugins.mail.service.MailService.CHILD_TYPE;
import static de.deepamehta.plugins.mail.service.MailService.PARENT_TYPE;
import static de.deepamehta.plugins.mail.service.MailService.TOPIC_TYPE;
import java.util.ArrayList;
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
    private Map<RelatedTopic, Topic> searchParentTypes;
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

    public Collection<RelatedTopic> getSearchParentTypes() {
        return revealSearchParentTypes().keySet();
    }

    public List<RelatedTopic> getSearchTypes() {
        if (searchTypes == null) {
            log.info("Initializing search types in mail plugin configuration cache");
            // get aggregated composite search types
            // FIXME use a specific association type and field renderer
            searchTypes = getConfiguration().getRelatedTopics(AGGREGATION, PARENT, CHILD, TOPIC_TYPE);
            for (RelatedTopic searchType : searchTypes) {
                log.info("=> search type " + searchType);
            }
        }
        return searchTypes;
    }

    public Set<String> getSearchTypeUris() {
        if (searchTypeUris == null) {
            log.info("Get search type URIs");
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

    private Map<RelatedTopic, Topic> revealSearchParentTypes() {
        if (searchParentTypes == null) {
            searchParentTypes = new HashMap<RelatedTopic, Topic>();
            for (Topic type : getSearchTypes()) {
                List assocTypeUris = new ArrayList();
                assocTypeUris.add(AGGREGATION_DEFINITION);
                assocTypeUris.add(COMPOSITION_DEFINITION);
                List<RelatedTopic> parents = type.getRelatedTopics(assocTypeUris, CHILD_TYPE, PARENT_TYPE, null);
                for (RelatedTopic parentType : parents) {
                    log.info("Adding \"" + parentType + "\"");
                    searchParentTypes.put(parentType, type);
                }
            }
        }
        log.info("Identified " + searchParentTypes.size() + " search parent types to get a \"Write Mail\" command");
        return searchParentTypes;
    }

}
