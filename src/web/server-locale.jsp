<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.*"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define page bean for header and sidebar --%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    String localeCode = ParamUtils.getParameter(request,"localeCode");
    String timeZoneID = ParamUtils.getParameter(request,"timeZoneID");
    boolean save = request.getParameter("save") != null;

    Map errors = new HashMap();
    if (save) {
        Locale newLocale = null;
        if (localeCode != null) {
            newLocale = LocaleUtils.localeCodeToLocale(localeCode.trim());
            if (newLocale == null) {
                errors.put("localeCode","");
            }
            else {
                JiveGlobals.setLocale(newLocale);
                response.sendRedirect("server-locale.jsp?success=true");
                return;
            }
        }
        // Set the timezeone
        try {
            TimeZone tz = TimeZone.getTimeZone(timeZoneID);
            JiveGlobals.setTimeZone(tz);
        }
        catch (Exception e) {}
    }

    Locale locale = JiveGlobals.getLocale();

    // Get the time zone list.
    String[][] timeZones = LocaleUtils.getTimeZoneList();

    // Get the current time zone.
    TimeZone timeZone = JiveGlobals.getTimeZone();
%>

<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("locale.title");
    pageinfo.setTitle(title);
    pageinfo.setPageID("server-locale");
%>

<jsp:include page="top.jsp" flush="true">
    <jsp:param name="helpPage" value="edit_server_properties.html" />
</jsp:include>
<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="locale.title.info" />
</p>

<form action="server-locale.jsp" method="post" name="sform">

<fieldset>
    <legend><fmt:message key="locale.system.set" /></legend>
    <div style="padding-top:0.5em;">

        <p>
        <b><fmt:message key="locale.current" />:</b> <%= locale.getDisplayName(locale) %> /
            <%= LocaleUtils.getTimeZoneName(JiveGlobals.getTimeZone().getID(), locale) %>
        </p>

        <%  boolean usingPreset = false;
            Locale[] locales = Locale.getAvailableLocales();
            for (int i=0; i<locales.length; i++) {
                usingPreset = locales[i].equals(locale);
                if (usingPreset) { break; }
            }
        %>

        <p><b><fmt:message key="language.choose" />:</b></p>

        <table cellspacing="0" cellpadding="3" border="0">
        <tbody>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="cs_CZ" <%= ("cs_CZ".equals(locale.toString()) ? "checked" : "") %>
                     id="loc01" />
                </td>
                <td colspan="2">
                    <label for="loc01">Czech (cs_CZ)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="de" <%= ("de".equals(locale.toString()) ? "checked" : "") %>
                     id="loc02" />
                </td>
                <td colspan="2">
                    <label for="loc02">Deutsch (de)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="en" <%= ("en".equals(locale.toString()) ? "checked" : "") %>
                     id="loc03" />
                </td>
                <td colspan="2">
                    <label for="loc03">English (en)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="es" <%= ("es".equals(locale.toString()) ? "checked" : "") %>
                     id="loc04" />
                </td>
                <td colspan="2">
                    <label for="loc04">Espa&ntilde;ol (es)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="fr" <%= ("fr".equals(locale.toString()) ? "checked" : "") %>
                     id="loc05" />
                </td>
                <td colspan="2">
                    <label for="loc05">Fran&ccedil;ais (fr)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="nl" <%= ("nl".equals(locale.toString()) ? "checked" : "") %>
                     id="loc06" />
                </td>
                <td colspan="2">
                    <label for="loc06">Nederlands (nl)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="pt_BR" <%= ("pt_BR".equals(locale.toString()) ? "checked" : "") %>
                     id="loc07" />
                </td>
                <td colspan="2">
                    <label for="loc07">Portugu&ecirc;s Brasileiro (pt_BR)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="zh_CN" <%= ("zh_CN".equals(locale.toString()) ? "checked" : "") %>
                     id="loc08" />
                </td>
                <td>
                    <a href="#" onclick="document.sform.localeCode[1].checked=true; return false;"><img src="images/language_zh_CN.gif" border="0" /></a>
                </td>
                <td>
                    <label for="loc08">Simplified Chinese (zh_CN)</label>
                </td>
            </tr>
        </tbody>
        </table>

        <br>

        <p><b><fmt:message key="timezone.choose" />:</b></p>

        <select size="1" name="timeZoneID">
        <%  for (int i=0; i<timeZones.length; i++) {
                String selected = "";
                if (timeZone.getID().equals(timeZones[i][0].trim())) {
                    selected = " selected";
                }
        %>
            <option value="<%= timeZones[i][0] %>"<%= selected %>><%= timeZones[i][1] %>
        <%  } %>
        </select>
    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="<fmt:message key="global.save_settings" />">

</form>

<jsp:include page="bottom.jsp" flush="true" />