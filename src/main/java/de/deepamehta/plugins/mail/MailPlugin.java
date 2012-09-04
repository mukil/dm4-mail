package de.deepamehta.plugins.mail;

import static de.deepamehta.plugins.mail.TopicUtils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.mail.internet.InternetAddress;
import javax.mail.util.ByteArrayDataSource;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import de.deepamehta.core.Association;
import de.deepamehta.core.ResultSet;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.listener.PluginServiceArrivedListener;
import de.deepamehta.core.service.listener.PluginServiceGoneListener;
import de.deepamehta.core.service.listener.PostCreateTopicListener;
import de.deepamehta.core.util.DeepaMehtaUtils;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.mail.service.MailService;

@Path("/mail")
@Produces("application/json")
public class MailPlugin extends PluginActivator implements MailService, PostCreateTopicListener,
        PluginServiceArrivedListener, PluginServiceGoneListener {

    public static final String ATTACHMENTS = "attachments";

    public static final String EMAIL_ADDRESS = "dm4.contacts.email_address";

    public static final String MAIL = "dm4.mail";

    public static final String RECIPIENT = "dm4.mail.recipient";

    public static final String RECIPIENT_TYPE = "dm4.mail.recipient.type";

    public static final String SENDER = "dm4.mail.sender";

    private MailConfigurationCache config = null;

    private Logger log = Logger.getLogger(getClass().getName());

    private FilesService fileService;

    /**
     * @see #associateRecipient(long, Topic, String)
     */
    @GET
    @Path("{mail}/recipient/{recipient}")
    public Association associateRecipient(@PathParam("mail") long mailId,
            @PathParam("recipient") long recipientId, @QueryParam("type") String type) {
        try {
            RecipientType rType = null;
            if (type == null || type.isEmpty()// type URI is unknown?
                    || config.getRecipientTypeUris().contains(type) == false) {
                log.fine("use default recipient type");
                rType = config.getDefaultRecipientType();
            } else {
                rType = RecipientType.fromUri(type);
            }
            return associateRecipient(mailId, dms.getTopic(recipientId, true, null), rType);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Association associateRecipient(long mailId, Topic recipient, RecipientType type) {
        log.info("associate " + mailId + " with recipient " + recipient.getId());
        List<TopicModel> addresses = recipient.getCompositeValue().getTopics(EMAIL_ADDRESS);
        if (addresses.size() < 1) {
            throw new IllegalArgumentException("recipient must have at least one email");
        }

        // create and return association
        AssociationModel association = new AssociationModel(RECIPIENT,//
                new TopicRoleModel(recipient.getId(), PART),//
                new TopicRoleModel(mailId, WHOLE),//
                new CompositeValue()//
                        .putRef(RECIPIENT_TYPE, type.getUri())// use the first email
                        .putRef(EMAIL_ADDRESS, addresses.get(0).getId()));
        return dms.createAssociation(association, null);
    }

    /**
     * @see #associateSender(long, Topic)
     */
    @GET
    @Path("{topic}/sender/{sender}")
    public Association associateSender(@PathParam("topic") long mailId,
            @PathParam("sender") long senderId) {
        try {
            return associateSender(mailId, dms.getTopic(senderId, true, null));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    public Association associateSender(long mailId, Topic sender) {
        log.info("associate " + mailId + " with sender " + sender.getId());

        List<TopicModel> addresses = sender.getCompositeValue().getTopics(EMAIL_ADDRESS);
        if (addresses.size() < 1) {
            throw new IllegalArgumentException("sender must have at least one email");
        }

        AssociationModel association = new AssociationModel(SENDER,//
                new TopicRoleModel(sender.getId(), PART),//
                new TopicRoleModel(mailId, WHOLE),//
                new CompositeValue()// use the first email
                        .putRef(EMAIL_ADDRESS, addresses.get(0).getId()));
        return dms.createAssociation(association, null);
    }

    /**
     * Returns the parent of each search type substring match.
     * 
     * @param term
     *            String to search.
     * @param clientState
     * @return Parent model of each result topic.
     */
    @GET
    @Path("/autocomplete/{term}")
    public ResultSet<TopicModel> autocomplete(@PathParam("term") String term) {
        try {
            log.info("autocomplete " + term);
            // hash parent results by ID to overwrite duplicates
            Map<Long, TopicModel> results = new HashMap<Long, TopicModel>();
            for (String uri : config.getSearchTypeUris()) {
                String parentTypeUri = config.getParentOfSearchType(uri).getUri();
                for (Topic topic : dms.searchTopics(term, uri, false, null)) {
                    Topic parentTopic = TopicUtils.getParentTopic(topic, parentTypeUri);
                    results.put(parentTopic.getId(), parentTopic.getModel());
                }
            }
            return new ResultSet<TopicModel>(results.size(), new HashSet<TopicModel>(
                    results.values()));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Create a new mail with one recipient.
     * 
     * @param recipientId
     *            ID of a recipient topic with at least one email address.
     * @return Mail topic with associated recipient.
     */
    @GET
    @Path("/create/{recipient}")
    public Topic create(@PathParam("recipient") long recipientId) {
        Topic mail = null;
        log.info("write a mail to recipient " + recipientId);
        try {
            mail = dms.createTopic(new TopicModel(MAIL), null);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
        associateRecipient(mail.getId(), recipientId, null);
        return mail;
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
     * Set the default sender of a mail topic after creation.
     */
    @Override
    public void postCreateTopic(Topic topic, ClientState clientState, Directives directives) {
        if (topic.getTypeUri().equals(MAIL)) {
            log.info("set default mail sender of mail " + topic.getTypeUri());
            TopicAssociation sender = config.getDefaultSender();
            if (sender != null) {
                TopicModel emailAddress = sender.getAssociation()//
                        .getCompositeValue().getTopic(EMAIL_ADDRESS);
                AssociationModel association = new AssociationModel(SENDER,//
                        new TopicRoleModel(sender.getTopic().getId(), PART),//
                        new TopicRoleModel(topic.getId(), WHOLE),//
                        new CompositeValue().putRef(EMAIL_ADDRESS, emailAddress.getId()));
                dms.createAssociation(association, clientState);
            }
        }
    }

    /**
     * Send a HTML mail.
     * 
     * @param mailId
     *            ID of a mail topic.
     * @param clientState
     * @return Sent mail topic.
     */
    @GET
    @Path("/send/{mail}")
    public Topic send(@PathParam("mail") long mailId, @HeaderParam("Cookie") ClientState clientState) {
        log.info("send mail " + mailId);
        try {
            Mail mail = new Mail(mailId, dms, clientState);
            InternetAddress sender = mail.getSender();
            Date now = new Date();

            HtmlEmail email = new HtmlEmail();
            email.setHostName(config.getSmtpHost());
            email.setFrom(sender.getAddress(), sender.getPersonal());
            email.setSubject(mail.getSubject());
            email.setTextMsg("Your email client does not support HTML messages");

            Document body = Jsoup.parse(mail.getBody());
            embedImages(email, body);
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
            return mail.setDate(now);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
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
            // TODO move the initialization to migration "0"
            try {
                // check attachment file repository
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
    }

    @Override
    public void pluginServiceGone(PluginService service) {
        if (service == fileService) {
            fileService = null;
        }
    }

    /**
     * Embed all images of body.
     */
    private void embedImages(HtmlEmail email, Document body) throws EmailException, IOException {
        int count = 0;
        for (Element image : body.getElementsByTag("img")) {
            URL url = new URL(image.attr("src"));
            String cid = embedImage(email, url, url.getPath() + ++count);
            image.attr("src", "cid:" + cid);
        }

    }

    /**
     * Embed any image type (external URL, file repository and plugin resource).
     * 
     * @return CID of embedded image
     */
    private String embedImage(HtmlEmail email, URL url, String name) throws EmailException,
            IOException {
        if (DeepaMehtaUtils.isDeepaMehtaURL(url)) {
            String path = fileService.getRepositoryPath(url);
            if (path != null) { // repository link
                log.fine("embed repository image " + path);
                return email.embed(fileService.getFile(path));
            } else { // plugin resource
                path = url.getPath();
                String pluginUri = path.substring(1, path.indexOf("/", 1));
                path = "/web" + path.substring(path.indexOf("/", 1));
                log.fine("embed image resource " + path + " of plugin " + pluginUri);
                String type = getMimeType(pluginUri, path);
                InputStream resource = dms.getPlugin(pluginUri).getResourceAsStream(path);
                return email.embed(new ByteArrayDataSource(resource, type), name);
            }
        } else { // external URL
            log.fine("embed external image " + url);
            return email.embed(url, name);
        }
    }

    // FIXME simplify MIME type determination
    private String getMimeType(String pluginUri, String path) throws IOException,
            FileNotFoundException {
        InputStream resource = dms.getPlugin(pluginUri).getResourceAsStream(path);
        if (resource == null) {
            throw new RuntimeException("resource " + path + " of plugin " + pluginUri
                    + "not accessible");
        }
        // copy resource to a temporary file
        File file = File.createTempFile("mail_image_resource", "bin");
        FileOutputStream writer = new FileOutputStream(file);
        IOUtils.copy(resource, writer);
        IOUtils.closeQuietly(writer);
        IOUtils.closeQuietly(resource);
        // open connection and get MIME type
        return file.toURI().toURL().openConnection().getContentType();
    }
}
