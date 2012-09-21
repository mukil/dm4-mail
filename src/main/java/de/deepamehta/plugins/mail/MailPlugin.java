package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.nodes.Document;

import de.deepamehta.core.Association;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.event.PluginServiceArrivedListener;
import de.deepamehta.core.service.event.PluginServiceGoneListener;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.plugins.accesscontrol.model.Operation;
import de.deepamehta.plugins.accesscontrol.model.Permissions;
import de.deepamehta.plugins.accesscontrol.model.UserRole;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.mail.service.MailService;

@Path("/mail")
@Produces(MediaType.APPLICATION_JSON)
public class MailPlugin extends PluginActivator implements //
        MailService,//
        PostCreateTopicListener,//
        PluginServiceArrivedListener,//
        PluginServiceGoneListener {

    public static final String ATTACHMENTS = "attachments";

    public static final String BODY = "dm4.mail.body";

    public static final String EMAIL_ADDRESS = "dm4.contacts.email_address";

    public static final String DATE = "dm4.mail.date";

    public static final String FILE = "dm4.files.file";

    public static final String FROM = "dm4.mail.from";

    public static final String MAIL = "dm4.mail";

    public static final String RECIPIENT = "dm4.mail.recipient";

    public static final String RECIPIENT_TYPE = "dm4.mail.recipient.type";

    public static final String SENDER = "dm4.mail.sender";

    public static final String SUBJECT = "dm4.mail.subject";

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    private FilesService fileService = null;

    private ImageCidEmbedment cidEmbedment = null;

    private MailConfigurationCache config = null;

    /**
     * @see #associateRecipient(long, Topic, String)
     */
    @POST
    @Path("{mail}/recipient/{recipient}")
    public Association associateRecipient(//
            @PathParam("mail") long mailId,//
            @PathParam("recipient") long recipientId,//
            @QueryParam("type") String type,//
            @HeaderParam("Cookie") ClientState cookie) {
        try {
            return associateRecipient(//
                    mailId,//
                    dms.getTopic(recipientId, true, cookie),//
                    config.checkRecipientType(type),//
                    cookie);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Association associateRecipient(long mailId, Topic recipient, RecipientType type,
            ClientState clientState) {
        log.info("associate " + mailId + " with recipient " + recipient.getId());

        // get and check email addresses
        List<TopicModel> addresses = recipient.getCompositeValue().getTopics(EMAIL_ADDRESS);
        if (addresses.size() < 1) {
            throw new IllegalArgumentException("recipient must have at least one email");
        }
        List<TopicModel> addressIds = new ArrayList<TopicModel>();
        for (TopicModel address : addresses) {
            addressIds.add(new TopicModel(address.getId()));
        }
        CompositeValue value = new CompositeValue()//
                .putRef(RECIPIENT_TYPE, type.getUri())//
                .put(EMAIL_ADDRESS, addressIds);

        // find existing recipient association
        Association association = dms.getAssociation(RECIPIENT, //
                mailId, recipient.getId(), WHOLE, PART, false, clientState);
        if (association == null) { // create a recipient association
            return createRecipient(mailId, recipient, value, clientState);
        } else { // update address and type references
            association.setCompositeValue(value, clientState, new Directives());
            return association;
        }
    }

    /**
     * @see #associateSender(long, Topic)
     */
    @POST
    @Path("{mail}/sender/{sender}")
    public Association associateSender(//
            @PathParam("mail") long mailId,//
            @PathParam("sender") long senderId,//
            @HeaderParam("Cookie") ClientState cookie) {
        try {
            return associateSender(mailId, dms.getTopic(senderId, true, cookie), cookie);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Association associateSender(long mailId, Topic sender, ClientState clientState) {
        log.info("associate " + mailId + " with sender " + sender.getId());

        // get and check email address
        List<TopicModel> addresses = sender.getCompositeValue().getTopics(EMAIL_ADDRESS);
        if (addresses.size() != 1) {
            throw new IllegalArgumentException("sender must have exactly one email address");
        }
        CompositeValue value = new CompositeValue().putRef(EMAIL_ADDRESS, addresses.get(0).getId());

        // find existing sender association
        RelatedTopic oldSender = dms.getTopic(mailId, false, clientState)//
                .getRelatedTopic(SENDER, WHOLE, PART, null, false, false, clientState);
        if (oldSender == null) { // create the first sender association
            return createSender(mailId, sender, value, clientState);
        } else { // update or delete the old sender
            Association association = dms.getAssociation(SENDER,//
                    mailId, oldSender.getId(), WHOLE, PART, false, clientState);
            if (sender.getId() != oldSender.getId()) { // delete the old one
                dms.deleteAssociation(association.getId(), clientState);
                association = createSender(mailId, sender, value, clientState);
            } else { // update composite
                association.setCompositeValue(value, clientState, new Directives());
            }
            return association;
        }
    }

    /**
     * Returns the parent of each search type substring match.
     * 
     * @param term
     *            String to search.
     * @param cookie
     *            Actual cookie.
     * @return Parent model of each result topic.
     */
    @GET
    @Path("/autocomplete/{term}")
    public ResultSet<TopicModel> autocomplete(@PathParam("term") String term,
            @HeaderParam("Cookie") ClientState cookie) {
        log.info("autocomplete " + term);
        try {
            // hash parent results by ID to overwrite duplicates
            Map<Long, TopicModel> results = new HashMap<Long, TopicModel>();
            for (String uri : config.getSearchTypeUris()) {
                String parentTypeUri = config.getParentOfSearchType(uri).getUri();
                for (Topic topic : dms.searchTopics(term, uri, false, cookie)) {
                    Topic parentTopic = TopicUtils.getParentTopic(topic, parentTypeUri);
                    results.put(parentTopic.getId(), parentTopic.getModel());
                }
            }
            return new ResultSet<TopicModel>(results.size(), //
                    new HashSet<TopicModel>(results.values()));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Creates a copy of mail.
     * 
     * @param mailId
     *            ID of the mail topic to clone.
     * @param cookie
     *            Actual cookie.
     * @return Cloned mail topic with associated sender and recipients.
     */
    @POST
    @Path("/{mail}/copy")
    public Topic copyMail(@PathParam("mail") long mailId, @HeaderParam("Cookie") ClientState cookie) {
        log.info("copy mail " + mailId);
        try {
            Topic mail = dms.getTopic(mailId, true, cookie);
            TopicModel model = new TopicModel(MAIL, mail.getCompositeValue().put(DATE, ""));
            Topic topic = dms.createTopic(model, cookie);

            // copy sender association
            TopicAssociation sender = TopicUtils.getRelatedPart(dms, mail, SENDER);
            dms.createAssociation(new AssociationModel(SENDER,//
                    new TopicRoleModel(sender.getTopic().getId(), PART),//
                    new TopicRoleModel(topic.getId(), WHOLE),//
                    sender.getAssociation().getCompositeValue()), cookie);

            // copy recipient associations
            for (TopicAssociation recipient : TopicUtils.getRelatedParts(dms, mail, RECIPIENT)) {
                dms.createAssociation(new AssociationModel(RECIPIENT,//
                        new TopicRoleModel(recipient.getTopic().getId(), PART),//
                        new TopicRoleModel(topic.getId(), WHOLE),//
                        recipient.getAssociation().getCompositeValue()), cookie);
            }
            return topic;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Creates a new mail with one recipient.
     * 
     * @param recipientId
     *            ID of a recipient topic with at least one email address.
     * @param cookie
     *            Actual cookie.
     * @return Mail topic with associated recipient.
     */
    @POST
    @Path("/write/{recipient}")
    public Topic writeTo(@PathParam("recipient") long recipientId,
            @HeaderParam("Cookie") ClientState cookie) {
        log.info("write a mail to recipient " + recipientId);
        try {
            Topic mail = dms.createTopic(new TopicModel(MAIL), cookie);
            associateRecipient(//
                    mail.getId(),//
                    dms.getTopic(recipientId, true, cookie),//
                    config.getDefaultRecipientType(),//
                    cookie);
            return mail;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * @return Default recipient type.
     */
    @GET
    @Path("/recipient/default")
    public String getDefaultRecipientType() {
        try {
            return config.getDefaultRecipientType().getUri();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * @return Recipient types.
     */
    @GET
    @Path("/recipient/types")
    public ResultSet<Topic> getRecipientTypes() {
        try {
            return config.getRecipientTypes();
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * @see #getSearchParentTypes
     */
    @GET
    @Path("/search/parents")
    public ResultSet<Topic> listSearchParentTypes() {
        try {
            Collection<Topic> parents = getSearchParentTypes();
            return new ResultSet<Topic>(parents.size(), new HashSet<Topic>(parents));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Collection<Topic> getSearchParentTypes() {
        return config.getSearchParentTypes();
    }

    /**
     * Load the configuration.
     * 
     * Useful after type and configuration changes with the web-client.
     */
    @GET
    @Path("/config/load")
    public void loadConfiguration() {
        try {
            log.info("load mail configuration");
            config = new MailConfigurationCache(dms);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Sets the default sender of a mail topic after creation.
     */
    @Override
    public void postCreateTopic(Topic topic, ClientState clientState, Directives directives) {
        if (topic.getTypeUri().equals(MAIL)) {
            try {
                TopicModel from = topic.getCompositeValue().getTopic(FROM);
                if (from.getSimpleValue().booleanValue() == false) {
                    associateDefaultSender(topic, clientState);
                }
            } catch (RuntimeException e) { // TODO use specific exception
                if (e.getMessage().contains("Invalid access to CompositeValue entry")) {
                    associateDefaultSender(topic, clientState);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * @see #send(Mail)
     */
    @POST
    @Path("/{mail}/send")
    public Topic send(@PathParam("mail") long mailId, @HeaderParam("Cookie") ClientState cookie) {
        log.info("send mail " + mailId);
        try {
            return send(new Mail(mailId, dms, cookie));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Topic send(Mail mail) throws UnsupportedEncodingException, EmailException, IOException {
        InternetAddress sender = mail.getSender();
        HtmlEmail email = new HtmlEmail();
        email.setHostName(config.getSmtpHost());
        email.setFrom(sender.getAddress(), sender.getPersonal());
        email.setSubject(mail.getSubject());

        Document body = cidEmbedment.embedImages(email, mail.getBody());
        String text = body.text();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty message");
        }
        email.setTextMsg(text);
        email.setHtmlMsg(body.html());

        for (Long fileId : mail.getAttachmentIds()) {
            String path = fileService.getFile(fileId).getAbsolutePath();
            EmailAttachment attachment = new EmailAttachment();
            attachment.setPath(path);
            log.fine("attach " + path);
            email.attach(attachment);
        }

        Map<RecipientType, List<InternetAddress>> recipients = mail.getRecipients();
        for (RecipientType type : recipients.keySet()) {
            switch (type) {
            case BCC:
                email.setBcc(recipients.get(type));
                break;
            case CC:
                email.setCc(recipients.get(type));
                break;
            case TO:
                email.setTo(recipients.get(type));
                break;
            default:
                throw new IllegalArgumentException("unsupported recipient type " + type);
            }
        }
        email.send();
        return mail.setDate(new Date());
    }

    /**
     * Initialize configuration cache.
     */
    @Override
    public void setCoreService(DeepaMehtaService dms) {
        super.setCoreService(dms);
        log.info("core service reference change");
        loadConfiguration();
    }

    /**
     * Reference file service and create the attachment directory if not exists.
     */
    @Override
    public void pluginServiceArrived(PluginService service) {
        if (service instanceof FilesService) {
            log.fine("file service arrived");
            fileService = (FilesService) service;
            createAttachmentDirectory();
            cidEmbedment = new ImageCidEmbedment(dms, fileService);
        } else if (service instanceof AccessControlService) {
            checkACLsOfMigration((AccessControlService) service);
        }
    }

    @Override
    public void pluginServiceGone(PluginService service) {
        if (service == fileService) {
            fileService = null;
            cidEmbedment = null;
        }
    }

    private Association createRecipient(long mailId, Topic recipient, CompositeValue value,
            ClientState clientState) {
        return dms.createAssociation(new AssociationModel(RECIPIENT,//
                new TopicRoleModel(recipient.getId(), PART),//
                new TopicRoleModel(mailId, WHOLE), value), clientState);
    }

    private Association createSender(long mailId, Topic sender, CompositeValue value,
            ClientState clientState) {
        return dms.createAssociation(new AssociationModel(SENDER,//
                new TopicRoleModel(sender.getId(), PART),//
                new TopicRoleModel(mailId, WHOLE), value), clientState);
    }

    private void associateDefaultSender(Topic mail, ClientState clientState) {
        TopicAssociation sender = config.getDefaultSender();
        if (sender != null) {
            log.fine("set default sender of mail " + mail.getId());
            createSender(mail.getId(), sender.getTopic(),//
                    sender.getAssociation().getCompositeValue(), clientState);
        }
    }

    private void createAttachmentDirectory() {
        // TODO move the initialization to migration "0"
        try {
            ResourceInfo resourceInfo = fileService.getResourceInfo(ATTACHMENTS);
            String kind = resourceInfo.toJSON().getString("kind");
            if (kind.equals("directory") == false) {
                String repoPath = System.getProperty("dm4.filerepo.path");
                String message = "attachment storage directory " + repoPath + File.separator
                        + ATTACHMENTS + " can not be used";
                throw new IllegalStateException(message);
            }
        } catch (WebApplicationException e) { // !exists
            // catch fileService info request error => create directory
            if (e.getResponse().getStatus() != 404) {
                throw e;
            } else {
                log.info("create attachment directory");
                fileService.createFolder(ATTACHMENTS, "/");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkACLsOfMigration(AccessControlService acs) {
        Topic config = dms.getTopic("uri", new SimpleValue("dm4.mail.config"), false, null);
        if (acs.getCreator(config) == null) {
            log.info("initial ACL update of configuration");
            Topic admin = acs.getUsername("admin");
            acs.setCreator(config, admin.getId());
            acs.setOwner(config, admin.getId());
            acs.createACLEntry(config, UserRole.OWNER,//
                    new Permissions().add(Operation.WRITE, true));
        }
    }
}
