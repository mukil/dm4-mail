package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;
import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.SimpleValue;
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

    private String defaultRecipientType = null;

    private TopicAssociation defaultSender = null;

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

    private Topic getConfiguration() {
        if (config == null) {
            log.info("reveal mail plugin configuration");
            config = dms.getTopic("uri", new SimpleValue(MAIL_CONFIG), true, null);
        }
        return config;
    }

    public String getDefaultRecipientType() {
        if (defaultRecipientType == null) {
            log.info("reveal default recipient type");
            defaultRecipientType = getConfiguration()//
                    .getCompositeValue().getTopic(RECIPIENT_TYPE).getUri();
        }
        return defaultRecipientType;
    }

    public TopicAssociation getDefaultSender() {
        if (defaultSender == null) {
            log.info("reveal default sender");
            defaultSender = TopicUtils.getRelatedPart(dms, getConfiguration(), SENDER);
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
            recipientTypeUris = new LinkedHashSet<String>();
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
            log.info("resolved: " + searchTypes.toJSON());
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
            smtpHost = getConfiguration()//
                    .getCompositeValue().getTopic(SMTP_HOST).getSimpleValue().toString();
        }
        return smtpHost;
    }

    private Map<String, Topic> revealSearchParentTypes() {
        if (searchParentTypes == null) {
            log.info("reveal search parent types");
            searchParentTypes = new HashMap<String, Topic>();
            for (Topic type : getSearchTypes()) {
                log.info("GET PARENT OF " + type.toJSON());
                Topic parentType = TopicUtils.getParentType(type);
                log.info("ADD PARENT " + parentType.toJSON());
                searchParentTypes.put(type.getUri(), parentType);
            }
        }
        return searchParentTypes;
    }

}
