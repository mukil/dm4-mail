package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.MailPlugin.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.DeepaMehtaService;

public class Autocomplete {

    private final DeepaMehtaService dms;
    private final MailConfigurationCache config;

    public static final Comparator<TopicModel> VALUE_COMPARATOR = new Comparator<TopicModel>() {
        @Override
        public int compare(TopicModel a, TopicModel b) {
            return a.getSimpleValue().toString().compareTo(b.getSimpleValue().toString());
        }
    };

    public Autocomplete(DeepaMehtaService dms, MailConfigurationCache config) {
        this.dms = dms;
        this.config = config;
    }

    public List<TopicModel> search(String query, ClientState clientState) {
        // search and hash parent results by ID (overwrites duplicated)
        Map<Long, Topic> parents = new HashMap<Long, Topic>();
        for (String uri : config.getSearchTypeUris()) {
            for (Topic topic : dms.searchTopics(query, uri, clientState)) {
                Topic parent = getParent(topic);
                parents.put(parent.getId(), parent);
            }
        }

        // get and hash addresses of each parent
        Map<Long, TopicModel> addresses = new HashMap<Long, TopicModel>();
        for (Topic result : parents.values()) {
            Topic parent = dms.getTopic(result.getId(), true, clientState);
            for (TopicModel address : parent.getCompositeValue().getTopics(EMAIL_ADDRESS)) {
                putAddress(addresses, parent, address);
            }
        }

        // search email directly afterwards and merge the results
        Set<Topic> searchTopics = dms.searchTopics(query, EMAIL_ADDRESS, clientState);
        for (Topic address : searchTopics) {
            TopicModel model = address.getModel();
            if (addresses.containsKey(model.getId()) == false) {
                putAddress(addresses, getParent(address), model);
            }
        }

        // wrap, sort and return
        List<TopicModel> result = new ArrayList<TopicModel>(addresses.values());
        Collections.sort(result, VALUE_COMPARATOR);
        return result;
    }

    private RelatedTopic getParent(Topic child) {
        return child.getRelatedTopic(COMPOSITION, PART, WHOLE, null, false, false, null);
    }

    /**
     * puts an inconsistent model (address ID + contact type URI) of email
     * address into the addresses map.
     */
    private void putAddress(Map<Long, TopicModel> addresses, Topic parent, TopicModel address) {
        // concatenate values
        address.setSimpleValue(parent.getSimpleValue() + //
                " &lt;" + address.getSimpleValue() + "&gt;");
        // replace type URI
        address.setTypeUri(parent.getTypeUri());
        addresses.put(address.getId(), address);
    }
}
