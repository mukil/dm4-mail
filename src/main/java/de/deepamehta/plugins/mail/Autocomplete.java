package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.CoreService;

public class Autocomplete {

    private static Logger log = Logger.getLogger(Autocomplete.class.getName());

    private final CoreService dms;

    private final MailConfigurationCache config;

    public static final Comparator<TopicModel> VALUE_COMPARATOR = new Comparator<TopicModel>() {
        @Override
        public int compare(TopicModel a, TopicModel b) {
            return a.getSimpleValue().toString().compareTo(b.getSimpleValue().toString());
        }
    };

    public Autocomplete(CoreService dms, MailConfigurationCache config) {
        this.dms = dms;
        this.config = config;
    }

    /**
     * call a search on all configured topic types and on all email addresses
     * 
     * @param query
     * 
     * @return topic list with email ID and contact type URI
     */
    public List<TopicModel> search(String query) {
        // search and hash parent results by ID (to overwrite duplicates)
        Map<Long, Topic> parents = new HashMap<Long, Topic>();
        for (String uri : config.getSearchTypeUris()) {
            for (Topic topic : dms.searchTopics(query, uri)) {
                Topic parent = getParent(topic);
                parents.put(parent.getId(), parent);
            }
        }

        // get and hash addresses of each parent
        Map<Long, TopicModel> addresses = new HashMap<Long, TopicModel>();
        for (Topic result : parents.values()) {
            Topic parent = dms.getTopic(result.getId()).loadChildTopics();
            List<? extends RelatedTopicModel> mailAdresses = getEmailAddresses(parent);
            if (mailAdresses != null) {
                for (TopicModel address : getEmailAddresses(parent)) {
                    putAddress(addresses, parent, address);
                }
            }
        }

        // search email directly afterwards and merge the results
        List<Topic> searchTopics = dms.searchTopics(query, EMAIL_ADDRESS);
        for (Topic address : searchTopics) {
            TopicModel model = address.getModel();
            if (addresses.containsKey(model.getId()) == false) {
                putAddress(addresses, getParent(address), model);
            }
        }

        // wrap, sort and return
        List<TopicModel> result = new ArrayList<TopicModel>(addresses.values());
        Collections.sort(result, VALUE_COMPARATOR);

        // FIXME inconsistent model => use a specific view model
        return result;  
    }

    /**
     * 
     * @param topic
     * @param clientState
     * 
     * @return list of mail addresses with at minimum one empty address
     */
    private List<? extends RelatedTopicModel> getEmailAddresses(Topic topic) {
        // FIXME attached value should support add(...) of child compositions
        if (topic.getChildTopics().getTopicsOrNull(EMAIL_ADDRESS) == null) {
            log.warning("Composite of " + topic.getSimpleValue() + " contains NO EMAIL ADDRESS");
            return null;
        }
        // return the existing or the newly created list of addresses
        return topic.getChildTopics().getModel().getTopics(EMAIL_ADDRESS);
    }

    private RelatedTopic getParent(Topic child) {
        return child.getRelatedTopic(COMPOSITION, CHILD, PARENT, null);
    }

    /**
     * puts an inconsistent model (address ID + contact type URI) of email
     * address into the addresses map.
     */
    private void putAddress(Map<Long, TopicModel> addresses, Topic parent, TopicModel address) {
        // concatenate values
        if (address != null && parent != null) {
            address.setSimpleValue(parent.getSimpleValue() + "&lt;" + address.getSimpleValue() + "&gt;");
            // replace type URI
            address.setTypeUri(parent.getTypeUri());
            addresses.put(address.getId(), address);   
        }
    }
}
