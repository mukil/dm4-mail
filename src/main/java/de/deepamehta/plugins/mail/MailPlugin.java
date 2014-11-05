package de.deepamehta.plugins.mail;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
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
import org.codehaus.jettison.json.JSONException;
import org.jsoup.nodes.Document;

import de.deepamehta.core.Association;
import de.deepamehta.core.CompositeValue;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.CompositeValueModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.annotation.ConsumesService;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import de.deepamehta.plugins.accesscontrol.model.ACLEntry;
import de.deepamehta.plugins.accesscontrol.model.AccessControlList;
import de.deepamehta.plugins.accesscontrol.model.Operation;
import de.deepamehta.plugins.accesscontrol.model.UserRole;
import de.deepamehta.plugins.accesscontrol.service.AccessControlService;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.mail.service.MailService;
import java.util.ArrayList;

@Path("/mail")
@Produces(MediaType.APPLICATION_JSON)
public class MailPlugin extends PluginActivator implements MailService, PostCreateTopicListener {

    private static Logger log = Logger.getLogger(MailPlugin.class.getName());

    // URI constants

    public static final String AGGREGATION = "dm4.core.aggregation";
    public static final String COMPOSITION = "dm4.core.composition";
    public static final String CHILD = "dm4.core.child";
    public static final String CHILD_TYPE = "dm4.core.child_type";
    public static final String TOPIC_TYPE = "dm4.core.topic_type";
    public static final String PARENT = "dm4.core.parent";
    public static final String PARENT_TYPE = "dm4.core.parent_type";
    public static final String FILE = "dm4.files.file";
    public static final String ATTACHMENTS = "attachments";
    public static final String BODY = "dm4.mail.body";
    public static final String EMAIL_ADDRESS = "dm4.contacts.email_address";
    public static final String DATE = "dm4.mail.date";
    public static final String FROM = "dm4.mail.from";
    public static final String MAIL = "dm4.mail";
    public static final String MESSAGE_ID = "dm4.mail.id";
    public static final String RECIPIENT = "dm4.mail.recipient";
    public static final String RECIPIENT_TYPE = "dm4.mail.recipient.type";
    public static final String SENDER = "dm4.mail.sender";
    public static final String SIGNATURE = "dm4.mail.signature";
    public static final String SUBJECT = "dm4.mail.subject";
    public static final String USER_ACCOUNT = "dm4.accesscontrol.user_account";

    // service references

    private AccessControlService acService;
    private FilesService fileService = null;

    // package internal helpers

    MailConfigurationCache config = null;
    ImageCidEmbedment cidEmbedment = null;
    Autocomplete autocomplete = null;

    boolean isInitialized;

    /**
     * @see #associateRecipient(long, long, RecipientType, de.deepamehta.core.service.ClientState)
     */
    @POST
    @Path("{mail}/recipient/{address}")
    public Association associateRecipient(//
            @PathParam("mail") long mailId,//
            @PathParam("address") long addressId,//
            @HeaderParam("Cookie") ClientState cookie) {
        return associateRecipient(mailId, addressId, config.getDefaultRecipientType(), cookie);
    }

    @Override
    public Association associateRecipient(long mailId, long addressId, RecipientType type, ClientState clientState) {
        log.info("associate " + mailId + " with recipient address " + addressId);

        // create value of the recipient association (#593 ref?)
        CompositeValueModel value = new CompositeValueModel()//
                .putRef(RECIPIENT_TYPE, type.getUri())//
                .putRef(EMAIL_ADDRESS, addressId);

        // get and update or create a new recipient association
        RelatedTopic recipient = getContactOfEmail(addressId);
        Association association = getRecipientAssociation(mailId, addressId, recipient.getId());

        if (association == null) { // create a recipient association
            return associateRecipient(mailId, recipient, value, clientState);
        } else { // update address and type references
            association.setCompositeValue(value, clientState, new Directives());
            return association;
        }
    }

    @Override
    public void associateValidatedRecipients(long mailId, List<Topic> recipients, ClientState cookie) {
        for (Topic recipient : recipients) {
            Topic topic = dms.getTopic(recipient.getId(), true);
            if (topic.getCompositeValue().has(EMAIL_ADDRESS)) {
                String personal = recipient.getSimpleValue().toString();
                for (Topic email : topic.getCompositeValue().getTopics(EMAIL_ADDRESS)) {
                    String address = email.getSimpleValue().toString();
                    try {
                        new InternetAddress(address, personal).validate();
                    } catch (Exception e) {
                        log.log(Level.INFO, "email address '" + address + "' of contact '" + //
                                personal + "'" + " is invalid: " + e.getMessage());
                        continue; // check the next one
                    }
                    // associate validated email address as BCC recipient
                    associateRecipient(mailId, email.getId(), RecipientType.BCC, cookie);
                }
            }
        }
    }

    /**
     * @see #associateSender(long, long, de.deepamehta.core.service.ClientState)
     */
    @POST
    @Path("{mail}/sender/{address}")
    public Association mailSender(//
            @PathParam("mail") long mailId,//
            @PathParam("address") long addressId,//
            @HeaderParam("Cookie") ClientState cookie) {
        return associateSender(mailId, addressId, cookie);
    }

    @Override
    public Association associateSender(long mailId, long addressId, ClientState clientState) {
        log.info("associate " + mailId + " with sender " + addressId);

        // create value of sender association (#593 ref?)
        CompositeValueModel value = new CompositeValueModel().putRef(EMAIL_ADDRESS, addressId);

        // find existing sender association
        RelatedTopic sender = getContactOfEmail(addressId);
        RelatedTopic oldSender = getSender(mailId, false);

        if (oldSender == null) { // create the first sender association
            return associateSender(mailId, sender, value, clientState);
        } else { // update or delete the old sender
            Association association = getSenderAssociation(mailId, oldSender.getId());
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                if (sender.getId() != oldSender.getId()) { // delete the old one
                    dms.deleteAssociation(association.getId());
                    association = associateSender(mailId, sender, value, clientState);
                } else { // update composite
                    association.setCompositeValue(value, clientState, new Directives());
                }
                tx.success();
            } finally {
                tx.finish();
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
    public List<TopicModel> search(@PathParam("term") String term, //
            @HeaderParam("Cookie") ClientState cookie) {
        String query = "*" + term + "*";
        log.info("autocomplete " + query);
        return autocomplete.search(query, cookie);
    }

    /**
     * Creates a copy of the mail.
     *
     * @param mailId
     *            ID of the mail topic to clone.
     * @param includeRecipients
     *            Copy recipients of the origin?
     * @param cookie
     *            Actual cookie.
     * @return Cloned mail topic with associated sender and recipients.
     */
    @POST
    @Path("/{mail}/copy")
    public Topic copyMail(//
            @PathParam("mail") long mailId,//
            @QueryParam("recipients") boolean includeRecipients,//
            @HeaderParam("Cookie") ClientState cookie) {
        log.info("copy mail " + mailId);
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            Topic mail = dms.getTopic(mailId, true);
            TopicModel model = new TopicModel(MAIL, mail.getCompositeValue().getModel() // copy
                    .put(DATE, "").put(MESSAGE_ID, "")); // nullify date and ID
            Topic clone = dms.createTopic(model, cookie);

            // copy sender association
            RelatedTopic sender = getSender(mail, true); 
            // #593 ref?
            associateSender(clone.getId(), sender, sender.getRelatingAssociation()//
                    .getCompositeValue().getModel(), cookie);

            // copy recipient associations
            if (includeRecipients) {
                for (RelatedTopic recipient : mail.getRelatedTopics(RECIPIENT,//
                        PARENT, CHILD, null, false, true, 0)) {
                    for (Association association : dms.getAssociations(mail.getId(),//
                            recipient.getId())) {
                        if (association.getTypeUri().equals(RECIPIENT) == false) {
                            continue; // sender or something else found
                        }
                        // #593 ref?
                        CompositeValue value = dms.getAssociation(association.getId(), true).getCompositeValue();
                        associateRecipient(clone.getId(), recipient, value.getModel(), cookie);
                    }
                }
            }
            tx.success();
            return clone;
        } finally {
            tx.finish();
        }
    }

    /**
     * Creates a new mail to all email addresses of the contact topic.
     *
     * @param recipientId
     *            ID of a recipient contact topic.
     * @param cookie
     *            Actual cookie.
     * @return Mail topic with associated recipient.
     */
    @POST
    @Path("/write/{recipient}")
    public Topic writeTo(@PathParam("recipient") long recipientId,//
            @HeaderParam("Cookie") ClientState cookie) {
        log.info("write a mail to recipient " + recipientId);
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            Topic mail = dms.createTopic(new TopicModel(MAIL), cookie);
            Topic recipient = dms.getTopic(recipientId, true);
            if (recipient.getCompositeValue().has(EMAIL_ADDRESS)) {
                for (Topic address : recipient.getCompositeValue().getTopics(EMAIL_ADDRESS)) {
                    associateRecipient(mail.getId(), address.getId(),//
                            config.getDefaultRecipientType(), cookie);
                }
            }
            tx.success();
            return mail;
        } finally {
            tx.finish();
        }
    }

    /**
     * @return Default recipient type.
     */
    @GET
    @Path("/recipient/default")
    public String getDefaultRecipientType() {
        return config.getDefaultRecipientType().getUri();
    }

    /**
     * @return Recipient types.
     */
    @GET
    @Path("/recipient/types")
    public ResultList<RelatedTopic> getRecipientTypes() {
        return config.getRecipientTypes();
    }

    /**
     * @see #getSearchParentTypes
     */
    @GET
    @Path("/search/parents")
    public ResultList<Topic> listSearchParentTypes() {
        Collection<Topic> parents = getSearchParentTypes();
        return new ResultList<Topic>(parents.size(), new ArrayList<Topic>(parents));
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
    public Topic loadConfiguration() {
        log.info("load mail configuration");
        config = new MailConfigurationCache(dms);
        autocomplete = new Autocomplete(dms, config);
        return config.getTopic();
    }

    /**
     * Sets the default sender and signature of a mail topic after creation.
     */
    @Override
    public void postCreateTopic(Topic topic, ClientState clientState, Directives directives) {
        if (topic.getTypeUri().equals(MAIL)) {
            if (topic.getCompositeValue().has(FROM) == false) { // new mail
                associateDefaultSender(topic, clientState);
            } else { // copied mail
                Topic from = topic.getCompositeValue().getTopic(FROM);
                if (from.getSimpleValue().booleanValue() == false) { // sender?
                    associateDefaultSender(topic, clientState);
                }
            }
        }
    }

    /**
     * @see #send(Mail)
     */
    @POST
    @Path("/{mail}/send")
    public StatusReport send(//
            @PathParam("mail") long mailId) {
        log.info("send mail " + mailId);
        return send(new Mail(mailId, dms));
    }

    @Override
    public StatusReport send(Mail mail) {
        StatusReport statusReport = new StatusReport(mail.getTopic());

        HtmlEmail email = new HtmlEmail();
        email.setDebug(true); // => System.out.println(SMTP communication);
        email.setHostName(config.getSmtpHost());

        try {
            InternetAddress sender = mail.getSender();
            // ..) Set Senders of Mail
            email.setFrom(sender.getAddress(), sender.getPersonal());
        } catch (Exception e) {
            reportException(statusReport, Level.INFO, MailError.SENDER, e);
        }

        try {
            String subject = mail.getSubject();
            if (subject.isEmpty()) { // caught immediately
                throw new IllegalArgumentException("Subject of mail is empty");
            }
            // ..) Set Subject of Mail
            email.setSubject(subject);
        } catch (Exception e) {
            reportException(statusReport, Level.INFO, MailError.CONTENT, e);
        }

        try {
            Document body = cidEmbedment.embedImages(email, mail.getBody());
            String text = body.text();
            if (text.isEmpty()) { // caught immediately
                throw new IllegalArgumentException("Text body of mail is empty");
            }
            // ..) Set Message Body
            email.setTextMsg(text);
            email.setHtmlMsg(body.html());
        } catch (Exception e) {
            reportException(statusReport, Level.INFO, MailError.CONTENT, e);
        }

        for (Long fileId : mail.getAttachmentIds()) {
            try {
                String path = fileService.getFile(fileId).getAbsolutePath();
                EmailAttachment attachment = new EmailAttachment();
                attachment.setPath(path);
                log.fine("attach " + path);
                // ..) Attach Attachmentss
                email.attach(attachment);
            } catch (Exception e) {
                reportException(statusReport, Level.INFO, MailError.ATTACHMENTS, e);
            }
        }

        RecipientsByType recipients = new RecipientsByType();
        try {
            recipients = mail.getRecipients();
            try {
                // ..) Mapping Recipients
                mapRecipients(email, recipients);
            } catch (Exception e) {
                reportException(statusReport, Level.SEVERE, MailError.RECIPIENT_TYPE, e);
            }
        } catch (InvalidRecipients e) {
            for (String recipient : e.getRecipients()) {
                log.log(Level.INFO, MailError.RECIPIENTS.getMessage() + ": " + recipient);
                statusReport.addError(MailError.RECIPIENTS, recipient);
            }
        }

        if (statusReport.hasErrors()) {
            statusReport.setMessage("Mail can NOT be sent");
        } else { // send, update message ID and return status with attached mail
            try {
                String messageId = email.send();
                statusReport.setMessage("Mail was SUCCESSFULLY sent to " + //
                        recipients.getCount() + " mail addresses");
                mail.setMessageId(messageId);
            } catch (EmailException e) {
                statusReport.setMessage("Sending mail FAILED");
                reportException(statusReport, Level.SEVERE, MailError.SEND, e);
            } catch (Exception e) { // error after send
                reportException(statusReport, Level.SEVERE, MailError.UPDATE, e);
            }
        }
        return statusReport;
    }

    /**
     * Initialize.
     */
    @Override
    public void init() {
        isInitialized = true;
        configureIfReady();
    }

    /**
     * Reference file service and create the attachment directory if not exists.
     */
    @Override
    @ConsumesService({ "de.deepamehta.plugins.accesscontrol.service.AccessControlService",
            "de.deepamehta.plugins.files.service.FilesService" })
    public void serviceArrived(PluginService service) {
        if (service instanceof AccessControlService) {
            acService = (AccessControlService) service;
        } else if (service instanceof FilesService) {
            fileService = (FilesService) service;
            cidEmbedment = new ImageCidEmbedment(fileService);
        }
        configureIfReady();
    }

    private void configureIfReady() {
        if (isInitialized && acService != null && fileService != null) {
            createAttachmentDirectory();
            checkACLsOfMigration();
            loadConfiguration();
        }
    }

    @Override
    public void serviceGone(PluginService service) {
        if (service == acService) {
            acService = null;
        } else if (service == fileService) {
            fileService = null;
            cidEmbedment = null;
        }
    }

    private void checkACLsOfMigration() {
        Topic config = dms.getTopic("uri", new SimpleValue("dm4.mail.config"), false);
        if (acService.getCreator(config) == null) {
            log.info("initial ACL update of configuration");
            Topic admin = acService.getUsername("admin");
            String adminName = admin.getSimpleValue().toString();
            acService.setCreator(config, adminName);
            acService.setOwner(config, adminName);
            acService.setACL(config, new AccessControlList(new ACLEntry(Operation.WRITE, UserRole.OWNER)));
        }
    }

    private void createAttachmentDirectory() {
        // TODO move the initialization to migration "0"
        try {
            ResourceInfo resourceInfo = fileService.getResourceInfo(ATTACHMENTS);
            String kind = resourceInfo.toJSON().getString("kind");
            if (kind.equals("directory") == false) {
                String repoPath = System.getProperty("dm4.filerepo.path");
                String message = "attachment storage directory " + repoPath + File.separator + ATTACHMENTS
                        + " can not be used";
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param mail
     * @param clientState
     * 
     * @see MailPlugin#postCreateTopic(Topic, ClientState, Directives)
     */
    private void associateDefaultSender(Topic mail, ClientState clientState) {
        Topic creator = null;
        RelatedTopic sender = null;

        Topic creatorName = acService.getUsername(acService.getCreator(mail));
        if (creatorName != null) {
            creator = creatorName.getRelatedTopic(null, CHILD, PARENT, USER_ACCOUNT, false, false);
        }

        // get user account specific sender
        if (creator != null) {
            sender = getSender(creator, true);
        }

        // get the configured default sender instead
        if (sender == null) {
            sender = config.getDefaultSender();
        }

        if (sender != null) {
            DeepaMehtaTransaction tx = dms.beginTx();
            try {
                // ..) what is fetched here: the model of an association associated to AssocType sender?
                //     that would be an "E-Mail Address"-Value (being aggregated to a "Sender"-AssocType)
                CompositeValueModel value = sender.getRelatingAssociation().getCompositeValue().getModel();
                //     making: associateSender(MailTopic, SenderTopic, E-Mail Address Value)?
                associateSender(mail.getId(), sender, value, clientState);
                // 
                long addressId = value.getTopic(EMAIL_ADDRESS).getId();
                // ..) fetches (some topic being parent) related to "Sender"-Topic (most probably a signature topic )
                RelatedTopic signature = getContactSignature(sender, addressId);
                if (signature != null) {
                    // ### do we want to re-use or duplicate the signatures child topics ?
                    // ..) add a reference to the senders "Signature" to sent "Mail"
                    /** CompositeValueModel signatureModel = new CompositeValueModel();
                    signatureModel.add("dm4.mail.signature.name", signature.getModel()
                        .getCompositeValueModel().getTopic("dm4.mail.signature.name"));
                    signatureModel.add("dm4.mail.from", signature.getModel()
                        .getCompositeValueModel().getTopic("dm4.mail.from"));
                    signatureModel.add("dm4.mail.body", signature.getModel()
                        .getCompositeValueModel().getTopic("dm4.mail.body"));
                    TopicModel signatureCopy = new TopicModel(SIGNATURE, signatureModel);
                    log.info("DEBUG: Copied Signature: " + signature.getModel().toJSON()); **/
                    mail.getCompositeValue().getModel().add(SIGNATURE, signature.getModel());
                }
                tx.success();
            } finally {
                tx.finish();
            }
        }
    }

    private Association associateRecipient(long topicId, Topic recipient, CompositeValueModel value,
            ClientState clientState) {
        return dms.createAssociation(new AssociationModel(RECIPIENT,//
                new TopicRoleModel(recipient.getId(), CHILD),//
                new TopicRoleModel(topicId, PARENT), value), clientState);
    }

    private Association associateSender(long topicId, Topic sender, CompositeValueModel value, ClientState clientState) {
        return dms.createAssociation(new AssociationModel(SENDER,//
                new TopicRoleModel(sender.getId(), CHILD),//
                new TopicRoleModel(topicId, PARENT), value), clientState);
    }

    private RelatedTopic getContactOfEmail(long addressId) {
        return dms.getTopic(addressId, false).getRelatedTopic(COMPOSITION, CHILD, PARENT, null, false, false);
    }

    /** Comparing contact-signatures by "E-Mail Address"-Topic ID*/
    private RelatedTopic getContactSignature(Topic topic, long addressId) {
        for (RelatedTopic signature : topic.getRelatedTopics(SENDER, CHILD, PARENT, SIGNATURE, true, true, 0)) {
            CompositeValue value = signature.getRelatingAssociation().getCompositeValue();
            if (addressId == value.getTopic(EMAIL_ADDRESS).getId()) {
                return signature;
            }
        }
        return null;
    }

    private Association getRecipientAssociation(long topicId, long addressId, long recipientId) {
        for (Association recipient : dms.getAssociations(topicId, recipientId)) {
            Association association = dms.getAssociation(recipient.getId(), true);
            Topic address = association.getCompositeValue().getTopic(EMAIL_ADDRESS);
            if (association.getTypeUri().equals(RECIPIENT) && address.getId() == addressId) {
                return association;
            }
        }
        return null;
    }

    private RelatedTopic getSender(long topicId, boolean fetchRelatingComposite) {
        return getSender(dms.getTopic(topicId, false), fetchRelatingComposite);
    }

    private RelatedTopic getSender(Topic topic, boolean fetchRelatingComposite) {
        return topic.getRelatedTopic(SENDER, PARENT, CHILD, null, false, fetchRelatingComposite);
    }

    private Association getSenderAssociation(long topicId, long senderId) {
        return dms.getAssociation(SENDER, topicId, senderId, PARENT, CHILD, true);
    }

    private void mapRecipients(HtmlEmail email, Map<RecipientType, List<InternetAddress>> recipients)
            throws EmailException {
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
                throw new IllegalArgumentException(type.toString());
            }
        }
    }

    private void reportException(StatusReport report, Level level, MailError error, Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null) {
            message += ": " + cause.getMessage();
        }
        String logMessage = error.getMessage() + ": " + message;
        if (level == Level.WARNING || level == Level.SEVERE) {
            log.log(level, logMessage, e); // log the exception trace
        } else {
            log.log(level, logMessage); // log only the message
        }
        report.addError(error, message);
    }

}
