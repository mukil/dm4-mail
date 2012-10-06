package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;
import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.DeepaMehtaService;

/**
 * Reveals and caches mail configuration parts.
 * 
 * @TODO configure MTA user and password (requires a password data type)
 */
public class MailConfigurationCache {

    private static Logger log = Logger.getLogger(MailConfigurationCache.class.getName());

    public static final String MAIL_CONFIG = "dm4.mail.config";

    public static final String SMTP_HOST = "dm4.mail.config.host";

    private Topic config = null;

    private RecipientType defaultRecipientType = null;

    private RelatedTopic defaultSender = null;

    private boolean defaultSenderIsNull = false;

    private final DeepaMehtaService dms;

    private ResultSet<Topic> recipientTypes;

    private Set<String> recipientTypeUris;

    private Map<String, Topic> searchParentTypes;

    private ResultSet<RelatedTopic> searchTypes;

    private Set<String> searchTypeUris;

    private String smtpHost = null;

    public MailConfigurationCache(DeepaMehtaService dms) {
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
            log.fine("use default recipient type");
            return getDefaultRecipientType();
        } else {
            return RecipientType.fromUri(type);
        }
    }

    private Topic getConfiguration() {
        if (config == null) {
            log.info("reveal mail plugin configuration");
            config = dms.getTopic("uri", new SimpleValue(MAIL_CONFIG), true, null);
        }
        return config;
    }

    public RecipientType getDefaultRecipientType() {
        if (defaultRecipientType == null) {
            log.info("reveal default recipient type");
            TopicModel type = getConfiguration().getCompositeValue().getTopic(RECIPIENT_TYPE);
            defaultRecipientType = RecipientType.fromUri(type.getUri());
        }
        return defaultRecipientType;
    }

    public RelatedTopic getDefaultSender() {
        if (defaultSenderIsNull == false && defaultSender == null) {
            log.info("reveal default sender");
            defaultSender = getConfiguration().getRelatedTopic(SENDER,//
                    WHOLE, PART, null, false, true, null);
            if (defaultSender == null) {
                defaultSenderIsNull = true;
            }
        }
        return defaultSender;
    }

    public Topic getParentOfSearchType(String uri) {
        return revealSearchParentTypes().get(uri);
    }

    public ResultSet<Topic> getRecipientTypes() {
        if (recipientTypes == null) {
            log.info("reveal recipient types");
            recipientTypes = dms.getTopics(RECIPIENT_TYPE, false, 0, null);
        }
        return recipientTypes;
    }

    public Set<String> getRecipientTypeUris() {
        if (recipientTypeUris == null) {
            log.info("reveal recipient type URIs");
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

    public ResultSet<RelatedTopic> getSearchTypes() {
        if (searchTypes == null) {
            log.info("reveal search types");
            // get aggregated composite search types
            // FIXME use a specific association type and field renderer
            searchTypes = getConfiguration().getRelatedTopics(AGGREGATION,//
                    WHOLE, PART, TOPIC_TYPE, false, false, 0, null);
        }
        return searchTypes;
    }

    public Set<String> getSearchTypeUris() {
        if (searchTypeUris == null) {
            log.info("reveal search type URIs");
            searchTypeUris = new LinkedHashSet<String>();
            for (Topic topic : getSearchTypes()) {
                searchTypeUris.add(topic.getUri());
            }
        }
        return searchTypeUris;
    }

    public String getSmtpHost() {
        if (smtpHost == null) {
            log.info("reveal smtp host");
            smtpHost = getConfiguration().getCompositeValue()//
                    .getTopic(SMTP_HOST).getSimpleValue().toString();
        }
        return smtpHost;
    }

    private Map<String, Topic> revealSearchParentTypes() {
        if (searchParentTypes == null) {
            log.info("reveal search parent types");
            searchParentTypes = new HashMap<String, Topic>();
            for (Topic type : getSearchTypes()) {
                searchParentTypes.put(type.getUri(), TopicUtils.getParentType(type));
            }
        }
        return searchParentTypes;
    }

}
