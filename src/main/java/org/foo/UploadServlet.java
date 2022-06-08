package org.foo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.mail.EmailException;

public class UploadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String SEND = "send";

    public static final String DOWNLOAD = "download";

    private static final String S_ANDROIDALLOWEDCHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-+,@\u00A3$\u20AC!\u00BD\u00A7~'=()[]{}0123456789";

    private static final Set<Character> ANDROIDALLOWEDCHARS = makeAndroidAllowedChars();

    private static Set<Character> makeAndroidAllowedChars() {
        Set<Character> res = new HashSet<>();
        for (int i = 0; i < S_ANDROIDALLOWEDCHARS.length(); i++) {
            char c = S_ANDROIDALLOWEDCHARS.charAt(i);
            res.add(c);
        }
        return res;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
                                                                           throws ServletException,
                                                                               IOException {
        getServletContext().getRequestDispatcher("/page.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
                                                                                    throws ServletException,
                                                                                        IOException {
        String what = request.getParameter("what");
        String email = request.getParameter("email");
        for (Part part : request.getParts()) {
            if ("file".equals(part.getName())) {
                InputStream in = part.getInputStream();
                MsgConvert inst = new MsgConvert(in);
                try {
                    if (DOWNLOAD.equals(what)) {
                        final MimeMessage mimeMessage;
                        mimeMessage = inst.toEmailMime();
                        String msgFile = part.getSubmittedFileName();
                        response.setHeader("Content-Type", "message/rfc822");
                        response.setHeader(
                            "Content-Disposition",
                            makeContentDisposition(request, msgFile + ".eml"));
                        ServletOutputStream out = response.getOutputStream();
                        mimeMessage.writeTo(out);
                    } else if (SEND.equals(what) && email != null && !email.isEmpty()) {
                        String fullEmail = email.indexOf('@') != -1 ? email : (email + "@" + MsgConvert.DEFAULT_DOMAIN);
                        inst.setForwardFrom(fullEmail);
                        inst.setForwardTo(fullEmail);
                        inst.send(fullEmail);
                        request.setAttribute("sent", true);
                        request.setAttribute("email", email);
                        addCookie(request, response, "email", email);
                        getServletContext().getRequestDispatcher("/page.jsp").forward(request, response);
                    } else {
                        throw new ServletException("Unexpected input: what:" + what + "; email:" + email);
                    }
                    return;
                } catch (NamingException | EmailException | MessagingException e) {
                    throw new ServletException(e);
                }
            }
        }
        throw new ServletException("Mandatory file missing");
    }

    public static String tryDecodeUrlSafe(String s) {
        try {
            return new String(new Base64(true).decode(s), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static void addCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            String value) {
        String enc = new String(new Base64(-1, null, true).encodeToString(value.getBytes(StandardCharsets.UTF_8)));
        Cookie cookie = new Cookie(name, enc);
        cookie.setPath(request.getServletContext().getContextPath() + "/");
        cookie.setMaxAge(-1);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    private static String makeContentDisposition(HttpServletRequest request, String fileName) {
        // https://stackoverflow.com/a/6745788/447503
        String contentDisposition;
        String agent = request.getHeader("User-Agent");
        agent = agent == null ? "" : agent;

        if (agent.contains("MSIE 7.0") || agent.contains("MSIE 8.0")) {
            contentDisposition = "attachment; filename=" + urlEncode(fileName);
        } else if (agent.contains("android")) {
            // android built-in download manager (all browsers on android)
            contentDisposition =
                "attachment; filename=\"" + makeAndroidSafeFileName(fileName) + "\"";
        } else {
            contentDisposition =
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + urlEncode(
                    fileName);
        }
        return contentDisposition;
    }

    private static String urlEncode(String fileName) {
        try {
            return URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Can't happen", e); // NOSONAR
        }
    }

    private static String makeAndroidSafeFileName(String fileName) {
        char[] newFileName = fileName.toCharArray();
        for (int i = 0; i < newFileName.length; i++) {
            if (!ANDROIDALLOWEDCHARS.contains(newFileName[i])) {
                newFileName[i] = '_';
            }
        }
        return new String(newFileName);
    }
}
