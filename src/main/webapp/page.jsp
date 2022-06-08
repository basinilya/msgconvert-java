<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%><%--
--%><%@ page import="java.util.*" %><%--
--%><%@ page import="java.lang.reflect.*" %><%--
--%><%@ page import="javax.servlet.*" %><%--
--%><%@ page import="javax.servlet.http.*" %><%--
--%><%@ page import="org.apache.commons.text.StringEscapeUtils" %><%--
--%><%@ page import="org.foo.MsgConvert" %><%--
--%><%@ taglib prefix = "c" uri = "http://java.sun.com/jsp/jstl/core" %><%--
--%><%@ taglib prefix = "fmt" uri = "http://java.sun.com/jsp/jstl/fmt" %><%--
--%><%@ taglib prefix = "fn" uri = "http://java.sun.com/jsp/jstl/functions" %><%--
--%><%!
/** Helps when just "?a=b" turns into ";jsessionId=blah?a=b" */
private static String getOriginalRequestURI(HttpServletRequest request) {
    String uri = (String) request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
    if (uri == null) {
        uri = request.getServletPath();
    }
    return uri;
}
private static String defaultString(String s) {
    return s == null ? "" : s;
}
    %><%--
--%><%
            String email = defaultString((String)request.getAttribute("email"));
            String locale = defaultString(request.getParameter("locale"));
            if (!locale.isEmpty()) {
                org.foo.UploadServlet.addCookie(request, response, "locale", locale);
            }
            if (request.getCookies() != null) {
	            for (Cookie cookie : request.getCookies()) {
	                if ("email".equals(cookie.getName())) {
	                    if (email.isEmpty()) {
	                        email = org.foo.UploadServlet.tryDecodeUrlSafe(cookie.getValue());
	                    }
	                } else if ("locale".equals(cookie.getName())) {
	                    if (locale.isEmpty()) {
	                        locale = org.foo.UploadServlet.tryDecodeUrlSafe(cookie.getValue());
	                    }
	                }
	            }
            }
            request.setAttribute("email", email);
        	request.setAttribute("locale", locale);
        	request.setAttribute("selfPath", getOriginalRequestURI(request));
            %><%--
--%><%--
--%><%--
--%><%--
--%><c:if test="${!empty locale}"><fmt:setLocale value="${locale}"/></c:if><fmt:bundle basename="org.foo.LabelsBundle"><%--
--%><html>
<head>
<title><fmt:message key="MSG_Convert"/></title>
</head>
<body>
	<c:url var="url" value="${selfPath}"><%--
	--%><c:param name="locale" value="ru_RU"/><%--
	--%></c:url><a href="${url}">Русский</a>&nbsp;/&nbsp;<%--
	--%><c:url var="url" value="${selfPath}"><%--
	--%><c:param name="locale" value="en_US"/><%--
	--%></c:url><a href="${url}">English</a>&nbsp;&nbsp;&nbsp;&nbsp;
	<a target="_blank" href="https://github.com/basinilya/msgconvert-java"><fmt:message key="Project_Home"/></a>
	<form action="" method="post" enctype="multipart/form-data">
		<h1>
			<fmt:message key="Upload_an_msg_file">
				<fmt:param>
					<sup><a target="_blank"
						href="https://github.com/bbottema/outlook-message-parser/tree/master/src/test/resources/test-messages">*</a></sup>
				</fmt:param>
			</fmt:message>
		</h1>
		<p>
			<input id="file" name="file" type="file" accept=".msg" onchange="validateForm();" >
		</p>
		<p>
			<input id="whatChoiceDownload" name="what" type="radio" onclick="handleWhatClick(this);" value="download">
			<label for="whatChoiceDownload"><fmt:message key="Download"/></label>
			<input id="whatChoiceSend" name="what" type="radio" onclick="handleWhatClick(this);" value="send" checked="checked">
			<label for="whatChoiceSend"><fmt:message key="Forward"/></label>
		</p>
		<p>
			<label for="email"><fmt:message key="Email"/></label> <input id="email" oninput="validateForm();" name="email"
				type="text" value="${fn:escapeXml(email)}">@${fn:escapeXml(MsgConvert.DEFAULT_DOMAIN)}
		</p>
		<p>
			<input id="submit" type="submit" value="<fmt:message key="Submit"/>">
		</p>
	</form>
	<script type="text/javascript">
		var fileDom = document.getElementById("file");
		var submitDom = document.getElementById("submit");
		var emailDom = document.getElementById("email");
		var whatChoiceDownloadDom = document.getElementById("whatChoiceDownload");
		var whatChoiceSendDom = document.getElementById("whatChoiceSend");
		var checkedDom = whatChoiceDownloadDom.checked ? whatChoiceDownloadDom : (whatChoiceSendDom.checked ? whatChoiceSendDom : null);
		handleWhatClick(checkedDom); // also calls validateForm()
		function validateForm() {
		    var valid = !!(fileDom.value && (whatChoiceDownloadDom.checked || emailDom.value) );
			if (valid) {
			    submitDom.removeAttribute("disabled");
			} else {
			    submitDom.setAttribute("disabled", "disabled");
			}
		}
		function handleWhatClick(dom) {
			if (dom === whatChoiceSendDom) {
				emailDom.removeAttribute("disabled");
			} else {
				emailDom.setAttribute("disabled", "disabled");
			}
			validateForm();
		}
	</script><%--
--%><c:if test="${sent}">
	<hr>
	<h2><fmt:message key="successfully_sent"/></h2><%--
--%></c:if><%--
--%>
</body>
</html></fmt:bundle>
