<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.lang.reflect.Method,
                 java.io.File" %>

<%-- note, the loadClass method is defined in setup-global.jsp --%>

<%  // Check the user's environment for minimum requirements.

    boolean jdk13Installed = false;
    boolean servlet22Installed = false;
    boolean jsp11Installed = false;
    boolean jiveJarsInstalled = false;
    boolean jiveHomeExists = false;
    File jiveHome = null;

    // Check for JDK 1.3
    try {
        loadClass("java.util.concurrent.ConcurrentHashMap");
        jdk13Installed = true;
    }
    catch (ClassNotFoundException cnfe) {}
    // Check for Servlet 2.3:
    try {
        Class c = loadClass("javax.servlet.http.HttpSession");
        Method m = c.getMethod("getAttribute",new Class[]{String.class});
        servlet22Installed = true;
    }
    catch (ClassNotFoundException cnfe) {}
    // Check for JSP 1.1:
    try {
        loadClass("javax.servlet.jsp.tagext.Tag");
        jsp11Installed = true;
    }
    catch (ClassNotFoundException cnfe) {}
    // Check that the Messenger jar are installed:
    try {
        loadClass("org.jivesoftware.xmpp.XMPPServer");
        jiveJarsInstalled = true;
    }
    catch (ClassNotFoundException cnfe) {}

    // Try to determine what the jiveHome directory is:
    try {
        Class jiveGlobalsClass = loadClass("org.jivesoftware.xmpp.JiveGlobals");
        Method getJiveHomeMethod = jiveGlobalsClass.getMethod("getJiveHome", null);
        String jiveHomeProp = (String)getJiveHomeMethod.invoke(jiveGlobalsClass, null);
        if (jiveHomeProp != null) {
            jiveHome = new File(jiveHomeProp);
            if (jiveHome.exists()) {
                jiveHomeExists = true;
            }
        }
    }
    catch (Exception e) {
        e.printStackTrace();
    }

    // If there were no errors, redirect to the main setup page
    if (!jdk13Installed || !servlet22Installed || !jsp11Installed || !jiveJarsInstalled
            || !jiveHomeExists)
    {
%>
        <html>
        <head>
            <title><fmt:message key="title" bundle="${lang}" /> Setup</title>
            <link rel="stylesheet" type="text/css" href="style.css">
        </head>
        <body>

        <p class="jive-setup-page-header">
        <fmt:message key="title" bundle="${lang}" /> Setup
        </p>

        <p class="jive-setup-error-text">
        Error: Can not proceed with <fmt:message key="title" bundle="${lang}" /> setup.
        </p>

        <p>
        Your current installation fails to meet minimum <fmt:message key="title" bundle="${lang}" /> requirements - please see
        the checklist below:
        </p>

        <ul>
        <table cellpadding="3" cellspacing="2" border="0">
        <%  if (jdk13Installed) { %>

            <tr>
                <td><img src="images/check.gif" width="13" height="13" border="0"></td>
                <td>
                    At least JDK 1.5
                </td>
            </tr>

        <%  } else { %>

            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    At least JDK 1.5
                    </span>
                </td>
            </tr>

        <%  }
            if (servlet22Installed) {
        %>
            <tr>
                <td><img src="images/check.gif" width="13" height="13" border="0"></td>
                <td>
                    Servlet 2.2 Support
                </td>
            </tr>

        <%  } else { %>

            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    Servlet 2.2 Support
                    </span>
                </td>
            </tr>

        <%  }
            if (jsp11Installed) {
        %>
            <tr>
                <td><img src="images/check.gif" width="13" height="13" border="0"></td>
                <td>
                    JSP 1.1 Support
                </td>
            </tr>

        <%  } else { %>

            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    JSP 1.1 Support
                    </span>
                </td>
            </tr>

        <%  }
            if (jiveJarsInstalled) {
        %>
            <tr>
                <td><img src="images/check.gif" width="13" height="13" border="0"></td>
                <td>
                    <fmt:message key="title" bundle="${lang}" /> Classes
                </td>
            </tr>

        <%  } else { %>

            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    <fmt:message key="title" bundle="${lang}" /> Classes
                    </span>
                </td>
            </tr>

        <%  }
            if (jiveHomeExists) {
        %>
            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    Jive Home Directory (<%= jiveHome.toString() %>)
                    </span>
                </td>
            </tr>

        <%  } else { %>

            <tr>
                <td><img src="images/x.gif" width="13" height="13" border="0"></td>
                <td>
                    <span class="jive-setup-error-text">
                    Jive Home Directory - Not Set
                    </span>
                </td>
            </tr>

        <%  } %>
        </table>
        </ul>

        <p>
        Please read the installation documentation and try setting up your environment again. After making
        changes, restart your appserver and load this page again.
        </p>

        </body>
        </html>

<%      // return so we stop showing the page:
        return;
    }
%>