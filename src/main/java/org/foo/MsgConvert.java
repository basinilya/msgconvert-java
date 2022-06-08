package org.foo;


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;
import javax.naming.NamingException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.text.StringEscapeUtils;
import org.simplejavamail.outlookmessageparser.OutlookMessageParser;
import org.simplejavamail.outlookmessageparser.model.OutlookAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookFileAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookMessage;
import org.simplejavamail.outlookmessageparser.model.OutlookMsgAttachment;
import org.simplejavamail.outlookmessageparser.model.OutlookRecipient;

public class MsgConvert {

    public static final String DEFAULT_DOMAIN = System.getProperty("org.foo.MsgConvert.DEFAULT_DOMAIN");

    private static final String GATE1 = "gate1." + DEFAULT_DOMAIN;

    private static final String BOUNCE_ADDRESS = "<postmaster@" + DEFAULT_DOMAIN + ">";

    private static final String CLSNAME = MsgConvert.class.getName();

    private static final Set<String> SKIPHEADERS = makeSkipHeaders();

    private static final String NULL_IP = "0.0.0.0"; // 

    private static final String USAGE =
        "Usage:\n" //
            + "    java " + CLSNAME + " [options] <file.msg>...\n" //
            + "\n" //
            + "    " + CLSNAME + " --outfile <outfile> <file.msg>\n" //
            + "\n" //
            + "      Options:\n" //
            + "        --outfile <oufile> write message to <outfile> or - for STDOUT\n" //
            + "        --help             help message\n" //
            + "\n" //
            + "Options:\n" //
            + "\n" //
            + "    --outfile\n" //
            + "                Writes the message into the outfile instead of individual .eml files. For\n" //
            + "                STDOUT \"-\" can be used as outfile. This option cannot be used together with\n" //
            + "                multiple <file.msg> instances.\n" //
            + "\n" //
            + "    --help\n" //
            + "                Print a brief help message.\n" //
            + "";
    
    private static final Method M_GETPROPERTYVALUE =
        getMethod(OutlookMessage.class, "getPropertyValue", Integer.class);

    private final MyHtmlEmail mimeBuilder = new MyHtmlEmail();

    private final OutlookMessage msg;

    private boolean sending;

    private List<Entry<String, String>> headers = new ArrayList<>();

    private String forwardFrom;

    private String forwardTo;

    private boolean bogusFrom;

    private boolean bogusTo;

    private static String sOutFile;

    private static List<String> inFiles;

    private static OutputStream globalOut;

    private InternetHeaders parsedHeaders;

    private HtmlEmail cidHelper = new HtmlEmail();

    public MsgConvert(OutlookMessage msg)  {
        this.msg = msg;
        // without it buildMimeMessage() throws "Cannot find valid hostname for mail session"
        mimeBuilder.setHostName(NULL_IP);
    }

    public MsgConvert(InputStream msgStream) throws IOException {
        this( makeParser().parseMsg(msgStream));
    }

    public MsgConvert(File msgFile) throws IOException {
        // prefer File or String or FileInputStream for more efficient java.nio.channels.Channel
        // creation. Avoid BufferedInputStream
        this( makeParser().parseMsg(msgFile));
    }

    private static OutlookMessageParser makeParser() {
        OutlookMessageParser res = new OutlookMessageParser();
        res.setRtf2htmlConverter(CustomRTF2HTML.INSTANCE);
        return res;
    }

    private Object getPropertyValue(int code) {
        try {
            return M_GETPROPERTYVALUE.invoke(msg, code);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e); // 
        }
    }

    private static Set<String> makeSkipHeaders() {
        Set<String> res = new HashSet<>();
        for (String s : new String[] {
            "Message-ID",
            "Date",
            "Subject",
            "From",
            "To",
            "Cc",
            "MIME-Version",
            "Content-Type",
            "Content-Transfer-Encoding",
            "X-Mailer",
            "X-Msgconvert",
            "X-MS-Tnef-Correlator",
            "X-MS-Has-Attach" }) {
            res.add(s.toUpperCase());
        }
        return res;
    }

    private class MyHtmlEmail extends HtmlEmail {

        // make visible
        MimeMultipart getContainer2() {
            return super.getContainer();
        }

        @Override
        protected MimeMessage createMimeMessage(Session aSession) {
            return new MimeMessage(aSession) {

                @Override
                protected void updateMessageID() throws MessagingException {
                    // no-op - prevent generation of a new Message-ID
                    if (forwardTo != null) {
                        super.updateMessageID();
                    }
                }
            };
        }

        @Override
        public void buildMimeMessage() throws EmailException {
            super.buildMimeMessage();
            if (forwardTo == null) {
                try {
                    copyHeaderData(message);
                } catch (final MessagingException me) {
                    throw new EmailException(me);
                }
            }
        }
    }

    private void setHeaderFields() throws EmailException {
        // mime.setSubject(msg.getSubject( ))
        addHeaderField("Subject", msg.getSubject());

        setFrom();

        // List<InternetAddress> replyTos = new ArrayList<>()
        // mime.setReplyTo(replyTos)

        bogusTo = true;
        for (OutlookRecipient oTo : msg.getToRecipients()) {
            bogusTo = false;
            mimeBuilder.addTo(oTo.getAddress(), oTo.getName());
        }
        if (bogusTo) {
            // otherwise buildMimeMessage() throws "At least one receiver address required"
            mimeBuilder.addTo("bogus@domain.org");
        }

        for (OutlookRecipient oCc : msg.getCcRecipients()) {
            mimeBuilder.addCc(oCc.getAddress(), oCc.getName());
        }

        addHeaderField("Message-Id", msg.getMessageId());

        addHeaderField("In-Reply-To", (String) getPropertyValue(0x1042));
        addHeaderField("References", (String) getPropertyValue(0x1039));

        // String submissionId = (String)getPropertyValue(0x0047)

        mimeBuilder.setSentDate(msg.getCreationDate());
        mimeBuilder.setSentDate(msg.getClientSubmitTime());
        // not using msg.getDate() because it has no timezone info. Will copy the header later

        // String msgClass = msg.getMessageClass()

        // String displayBcc = msg.getDisplayBcc( )

        // Date lastModificationDate = msg.getLastModificationDate( )
        // OutlookSmime smime = msg.getSmime()
    }

    private void setFrom() throws EmailException {
        String fromString = getFromString();
        if (fromString != null) {
            mimeBuilder.setFrom(fromString);
        } else {
            if (msg.getFromEmail() != null) {
                mimeBuilder.setFrom(msg.getFromEmail(), msg.getFromName());
            } else {
                // otherwise buildMimeMessage() throws "From address required"
                bogusFrom = true;
                mimeBuilder.setFrom("bogus@domain.org");
            }
        }
    }

    private String getFromString() throws EmailException {
        // outlook-message-parser 1.7.9 improperly splits the header into address and personal
        // only use msg.getFromEmail() if not found in headers
        if (parsedHeaders != null) {
            String[] a = parsedHeaders.getHeader("From");
            if (a != null && a.length != 0) {
                return MimeUtility.unfold(a[0]);
            }
        }
        return null;
    }

    private void addHeaderField(String name, String value) {
        if (value != null && !value.isEmpty()) {
            headers.add(new AbstractMap.SimpleEntry<>(name, value));
        }
    }

    private void copyHeaderData(MimeMessage mimeMessage) throws MessagingException {
        if (bogusFrom) {
            mimeMessage.removeHeader("From");
        }
        if (bogusTo) {
            mimeMessage.removeHeader("To");
        }
        List<Header> reverseHeaders = new ArrayList<>();
        if (parsedHeaders != null) {
            if (sending) {
                // Don't want to bounce to the real mail author
                parsedHeaders.setHeader("Return-Path", BOUNCE_ADDRESS);
            }
            Enumeration<?> enu = parsedHeaders.getAllHeaders();
            while (enu.hasMoreElements()) {
                Header header = (Header) enu.nextElement();
                String name = header.getName();
                String value = header.getValue();
                if ("Date".equalsIgnoreCase(name)) {
                    mimeMessage.setHeader("Date", value);
                } else
                    if ("Received".equalsIgnoreCase(name) || "Return-Path".equalsIgnoreCase(name)) {
                        reverseHeaders.add(header);
                    } else if (!SKIPHEADERS.contains(name.toUpperCase())) {
                        addHeaderField(name, value);
                    }
            }
        }
        for (Entry<String, String> entry : headers) {
            mimeMessage.addHeader(entry.getKey(), entry.getValue());
        }
        for (int i = reverseHeaders.size() - 1; i >= 0; i--) {
            Header h = reverseHeaders.get(i);
            mimeMessage.addHeader(h.getName(), h.getValue());
        }
    }

    private void parseHeaders() throws MessagingException {
        String sHeaders = msg.getHeaders();
        if (sHeaders == null) {
            return;
        }
        // The parser does not support UTF-8 in headers, but the encoder can crash without UTF-8
        parsedHeaders = new InternetHeaders(IOUtils.toInputStream(sHeaders, StandardCharsets.UTF_8), true);
    }

    private void prepareBuilder() throws // 
        EmailException // 
        , MessagingException  //
    {
        parseHeaders();

        String forwardHeadText = makeForwardHeadText();

        String bodyText = msg.getBodyText();
        if (forwardHeadText != null || bodyText != null) {
            if (forwardHeadText != null) {
                bodyText = bodyText != null ? (forwardHeadText + "\n\n\n\n" + bodyText) : forwardHeadText;
            }
            mimeBuilder.setTextMsg(bodyText);
        }
        String html = msg.getBodyHTML();
        html = html != null ? html : msg.getConvertedBodyHTML();
        if (forwardHeadText != null || html != null) {
            if (forwardHeadText != null) {
                forwardHeadText = "<pre>"+ StringEscapeUtils.escapeHtml4(forwardHeadText) +"</pre><br/><br/><br/><br/>";
                html = html != null ? prependHtmlHeadText(forwardHeadText, html) : forwardHeadText;
            }
            mimeBuilder.setHtmlMsg(html);
        }

        for (OutlookAttachment oAtt : msg.getOutlookAttachments()) {
            if (oAtt instanceof OutlookFileAttachment) {
                OutlookFileAttachment fileAtt = (OutlookFileAttachment) oAtt;
                //Object some = fileAtt.getPropertyValue(0x3713)
                String contentId = fileAtt.getContentId();
                byte[] data = fileAtt.getData();
                // String name = fileAtt.getFilename()
                // String extension = fileAtt.getExtension()
                String longFilename = fileAtt.getLongFilename();
                String mimeTag = fileAtt.getMimeTag();
                // long size = fileAtt.getSize()
                ByteArrayDataSource ds = new ByteArrayDataSource(data, mimeTag);
                attachOrEmbed(html, contentId, ds, longFilename);
            }
            if (oAtt instanceof OutlookMsgAttachment) {
                OutlookMsgAttachment msgAtt = (OutlookMsgAttachment) oAtt;
                OutlookMessage nestedMsg = msgAtt.getOutlookMessage();
                MimeMessage nestedMimeMessage = new MsgConvert(nestedMsg).toEmailMime();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    nestedMimeMessage.writeTo(bos);
                } catch (IOException e) {
                    throw new RuntimeException("Can't happen", e); // 
                }
                ByteArrayDataSource ds = new ByteArrayDataSource(bos.toByteArray(), "message/rfc822");
                mimeBuilder.attach(ds, "", "");
            }
        }

        if (forwardTo != null) {
            mimeBuilder.addTo(forwardTo);
            mimeBuilder.setFrom(forwardFrom);
            mimeBuilder.setSubject("Fw: " + msg.getSubject());
        } else {
            setHeaderFields();
        }

        // commons-email 1.5 failed to fix EMAIL-147 properly and they don't know that
        // MimeBodyPart.setText(html,null,...) has actually set the correct charset in
        // MimeBodyPart.getDataHandler().getContentType().
        // This time they call MimeBodyPart.setContent(html,...) effectively resetting the
        // charset to empty which leads to broken unicode chars in html.
        // We set the global charset to avoid that.
        mimeBuilder.setCharset(StandardCharsets.UTF_8.name());
    }

    private String makeForwardHeadText() throws EmailException {
        String forwardHeadText = null;
        if (forwardTo != null) {
            String fromString = getFromString();
            if (fromString == null) {
                msg.getCcRecipients();
                fromString = msg.getFromName() + " <" + msg.getFromEmail() + ">";
            }
            StringBuilder sb = new StringBuilder();
            sb.append( "---- Forwarded by ").append( forwardFrom).append(" -----\n");
            sb.append( "From: ").append(fromString).append( "\n");
            sb.append( msg.getDate() );

            String delim;
            delim = "\nTo: ";
            for (OutlookRecipient oTo : msg.getToRecipients()) {
                sb.append(delim).append(oTo);
                delim = ", ";
            }
            delim = "\nCc: ";
            for (OutlookRecipient oCc : msg.getCcRecipients()) {
                sb.append(delim).append(oCc);
                delim = ", ";
            }
            sb.append("\nSubject: ").append(msg.getSubject());
            forwardHeadText = sb.toString();
        }
        return forwardHeadText;
    }

    static String prependHtmlHeadText(String forwardHeadText, String html) {
        // beginning of string not followed by <body> or position preceded by <body>
        Matcher m = Pattern.compile("(?im)<body[^>]*>").matcher(html);
        if (m.find()) {
            StringBuilder sb = new StringBuilder ();
            int end = m.end();
            sb.append(html, 0, end);
            sb.append("\n");
            sb.append(forwardHeadText);
            sb.append("\n");
            sb.append(html, end, html.length());
            return sb.toString();
        } else {
            return forwardHeadText + "\n" + html;
        }
    }

    public void send(String sForAddress) throws // 
        EmailException // 
        , MessagingException  //
        , NamingException //
        , IOException //
    {
        sending = true;
        prepareBuilder();
        InternetAddress forAddress = new InternetAddress(sForAddress, true);

        String[] mxDomains = getMailHost(forAddress);
        //mimeBuilder.start
        mimeBuilder.setSSLOnConnect(true);
        mimeBuilder.setSSLCheckServerIdentity(false);
        mimeBuilder.setHostName(mxDomains[0]);
        mimeBuilder.buildMimeMessage();
        MimeMessage mimeMessage = mimeBuilder.getMimeMessage();
        for (String mxDomain : mxDomains) {
            Properties props = mimeMessage.getSession().getProperties();
            if (GATE1.equalsIgnoreCase(mxDomain) && !StringUtils.isEmpty(props.getProperty("mail.smtp.socketFactory.class"))) {
                // known improperly configured
                props.setProperty("mail.smtp.socketFactory.class", MyMailSSLSocketFactory.class.getName());
            }
            Transport.send(mimeMessage, new Address[] { forAddress });
            break;
        }
    }

    public String[] getMailHost(InternetAddress forAddress) throws NamingException {
        String[] mxDomain;
        String domain = forAddress.getAddress().split("@")[1];
        if (DEFAULT_DOMAIN.equalsIgnoreCase(domain)) {
            mxDomain = new String[] { GATE1 };
        } else {
            mxDomain = MailHostsLookup.lookupMailHosts(domain);
        }
        return mxDomain;
    }

    public MimeMessage toEmailMime() throws // 
        EmailException // 
        , MessagingException  //
    {
        prepareBuilder();
        mimeBuilder.buildMimeMessage();
        MimeMessage mimeMessage = mimeBuilder.getMimeMessage();
        return mimeMessage;
    }

    private void attachOrEmbed /*  */ (String html, String contentId, DataSource ds, String longFilename) throws //
        EmailException, // 
        MessagingException //
    {
        if (html != null && contentId != null && !contentId.isEmpty()) {
            String cidString = "\"cid:" + contentId + "\"";
            if (html.contains(cidString )) {
                mimeBuilder.embed(ds, longFilename, contentId);
                // mimeBuilder.setSubType("related") // need to call "embed()" instead
                return;
            }
        }

        String description = "";
        mimeBuilder.attach(ds, longFilename, description);
        MimeMultipart container = mimeBuilder.getContainer2();
        MimeBodyPart bodyPart =
            (MimeBodyPart) container.getBodyPart(container.getCount() - 1);
        final String encodedCid = encodeRfc2392(contentId);
        bodyPart.setContentID("<" + encodedCid + ">");
    }


    private String encodeRfc2392(final String cid) throws EmailException 
    {
        if (cidHelper  == null) {
            cidHelper = new HtmlEmail();
        }
        return cidHelper
            .embed(new ByteArrayDataSource(new byte[1], "application/octet-stream"), "dummy", cid);
    }

    public static void main(final String[] args) throws Exception {
        sOutFile = null;
        inFiles = new ArrayList<>();
        parseArgs(args);

        try (Closeable globalClo = openGlobalOFile()) {
            for (String s : inFiles) {
                MsgConvert inst = new MsgConvert(new File(s));
                MimeMessage mimeMessage = inst.toEmailMime();
                try (OutputStream localClo = openLocalOFile(s)) {
                    OutputStream localOut =
                        localClo == null ? globalOut : new BufferedOutputStream(localClo);
                    mimeMessage.writeTo(localOut);
                }
            }
        }
    }

    private static OutputStream openLocalOFile(String s) throws FileNotFoundException {
        return globalOut != null ? null : new FileOutputStream(s + "-bbottema.eml");
    }

    private static OutputStream openGlobalOFile() throws IOException {
        globalOut = null;
        if ("-".equals(sOutFile)) {
            globalOut = getSysOut();
        } else if (sOutFile != null) {
            OutputStream clo = new FileOutputStream(sOutFile);
            try { // 
                globalOut = new BufferedOutputStream(clo);
            } finally {
                if (globalOut == null) {
                    clo.close();
                }
            }
            return clo;
        }
        return null;
    }

    private static PrintStream getSysOut() {
        return System.out; // 
    }

    private static void parseArgs(final String[] args) {
        PrintStream syserr = System.err; // 
        int i = 0;
        while (i < args.length) {
            if ("--outfile".equals(args[i])) {
                i++;
                sOutFile = args[i];
            } else if ("--help".equals(args[i])) {
                usage(getSysOut());
                System.exit(0); // 
            } else if ("--".equals(args[i])) {
                while (++i < args.length) {
                    inFiles.add(args[i]);
                }
                break;
            } else if (args[i].startsWith("-")) {
                syserr.println("Unknown option: " + args[i]);
                usage(syserr);
                System.exit(1); // 
            } else {
                inFiles.add(args[i]);
            }
            i++;
        }
        if (inFiles.isEmpty()) {
            syserr.println("No .msg files provided");
            usage(syserr);
            System.exit(1); // 
        }
        if (inFiles.size() > 1 && sOutFile != null) {
            syserr.println("The --outfile parameter does not allow to specify more than one <file.msg>. See --help for more details.");
            System.exit(1); // 
        }
    }

    private static Method //
        getMethod // 
        (final Class<?> clazz, final String name, Class<?>... params) {
        Method res = null;
        try {
            res = clazz.getDeclaredMethod(name, params);
            res.setAccessible(true);
            return res;
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e); // 
        }
    }

    private static final void usage(PrintStream out) {
        out.print(USAGE);
    }

    public HtmlEmail getMimeBuilder() {
        return mimeBuilder;
    }

    
    public String getForwardFrom() {
        return forwardFrom;
    }

    
    public void setForwardFrom(String forwardFrom) {
        this.forwardFrom = forwardFrom;
    }

    
    public String getForwardTo() {
        return forwardTo;
    }

    
    public void setForwardTo(String forwardTo) {
        this.forwardTo = forwardTo;
    }


}
