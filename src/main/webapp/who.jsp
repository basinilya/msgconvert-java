<%@ page language="java" contentType="text/plain; charset=UTF-8" pageEncoding="UTF-8"%><%--
--%><%@ page import="java.util.*" %><%--
--%><%@ page import="java.lang.reflect.*" %><%--
--%><%@ page import="javax.servlet.*" %><%--
--%><%@ page import="javax.servlet.http.*" %><%--
--%><%--
--%><% try { %><%--
--%>our host name: <%= java.net.InetAddress.getLocalHost().getHostName() %>
ServerName: <%= request.getServerName() %>
VirtualServerName: <%= request.getServletContext().getVirtualServerName() %>
ContextPath: <%= request.getContextPath() %>
LocalName: <%= request.getLocalName() %>
LocalPort: <%= request.getLocalPort() %>
RemoteHost: <%= request.getRemoteHost() %>
RemotePort: <%= request.getRemotePort() %>
isSecure: <%= request.isSecure() %>
scheme: <%= request.getScheme() %>
requestURL: <%= request.getRequestURL() %>
requestURI: <%= request.getRequestURI() %>
=============================================
<%
if (request.getCookies() != null) {
    for (Cookie cookie : request.getCookies()) {
    	String cookieVal = cookie.getValue();
    	String cookieName = cookie.getName();
    	%><%= cookieName %>: <%= cookieVal %>

<%
    }
}
%>=============================================
<%
Enumeration headerNames = request.getHeaderNames();
while (headerNames.hasMoreElements()) {
	String headerName = (String)headerNames.nextElement();
	Enumeration headerVals = request.getHeaders(headerName);
	while (headerVals.hasMoreElements()) {
		String headerVal = (String)headerVals.nextElement();
		%><%= headerName %>: <%= headerVal %>
<%
	}
}
/*
*/
%>=============================================
<%--
--%>
<%--
--%><%--
--%><%--
--%><%--
--%><%--
--%><%--
--%><%--
--%>
<% } catch (Exception e) { e.printStackTrace(new java.io.PrintWriter(out)); } %>
