/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.Log;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plugin servlet acts as a proxy for web requests (in the admin console)
 * to plugins. Since plugins can be dynamically loaded and live in a different place
 * than normal Jive Messenger admin console files, it's not possible to have them
 * added to the normal Jive Messenger admin console web app directory.<p>
 *
 * The servlet listens for requests in the form <tt>/plugins/[pluginName]/[JSP File]</tt>
 * (e.g. <tt>/plugins/foo/example.jsp</tt>). It also listens for image requests in the
 * the form <tt>/plugins/[pluginName]/images/*.png|gif</tt> (e.g.
 * <tt>/plugins/foo/images/example.gif</tt>).<p>
 *
 * JSP files must be compiled and available via the plugin's class loader. The mapping
 * between JSP name and servlet class files is defined in [pluginName]/web/web.xml.
 * Typically, this file is auto-generated by the JSP compiler when packaging the plugin.
 *
 * @author Matt Tucker
 */
public class PluginServlet extends HttpServlet {

    private static Map<String,HttpServlet> servlets;
    private static File pluginDirectory;
    private static ServletConfig servletConfig;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletConfig = config;
        servlets = new ConcurrentHashMap<String,HttpServlet>();
        pluginDirectory = new File(JiveGlobals.getMessengerHome(), "plugins");
    }

    public void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String pathInfo = request.getPathInfo();

        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        else {
            try {
                // Handle JSP requests.
                if (pathInfo.endsWith(".jsp")) {
                    handleJSP(pathInfo, request, response);
                    return;
                }
                // Handle image requests.
                else if (pathInfo.endsWith(".gif") || pathInfo.endsWith(".png")) {
                    handleImage(pathInfo, response);
                    return;
                }
                // Anything else results in a 404.
                else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
            catch (Exception e) {
                Log.error(e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
        }
    }

    /**
     * Registers all JSP page servlets for a plugin.
     *
     * @param manager the plugin manager.
     * @param plugin the plugin.
     * @param webXML the web.xml file containing JSP page names to servlet class file
     *      mappings.
     */
    public static void registerServlets(PluginManager manager, Plugin plugin, File webXML) {
        if (!webXML.exists()) {
            Log.error("Could not register plugin servlets, file " + webXML.getAbsolutePath() +
                    " does not exist.");
            return;
        }
        // Find the name of the plugin directory given that the webXML file
        // lives in plugins/[pluginName]/web/web.xml
        String pluginName = webXML.getParentFile().getParentFile().getName();
        try {
            SAXReader saxReader = new SAXReader();
            Document doc = saxReader.read(webXML);
            // Find all <servlet> entries to discover name to class mapping.
            List classes = doc.selectNodes("//servlet");
            Map<String,Class> classMap = new HashMap<String,Class>();
            for (int i=0; i<classes.size(); i++) {
                Element servletElement = (Element)classes.get(i);
                String name = servletElement.element("servlet-name").getTextTrim();
                String className = servletElement.element("servlet-class").getTextTrim();
                classMap.put(name, manager.loadClass(className, plugin));
            }
            // Find all <servelt-mapping> entries to discover name to URL mapping.
            List names = doc.selectNodes("//servlet-mapping");
            for (int i=0; i<names.size(); i++) {
                Element nameElement = (Element)names.get(i);
                String name = nameElement.element("servlet-name").getTextTrim();
                String url = nameElement.element("url-pattern").getTextTrim();
                // Register the servlet for the URL.
                Class servletClass = classMap.get(name);
                Object instance = servletClass.newInstance();
                if (instance instanceof HttpServlet) {
                    // Initialize the servlet then add it to the map..
                    ((HttpServlet)instance).init(servletConfig);
                    servlets.put(pluginName + url, (HttpServlet)instance);
                }
                else {
                    Log.warn("Could not load " + (pluginName + url) + ": not a servlet.");
                }
            }
        }
        catch (Throwable e) {
            Log.error(e);
        }
    }

    /**
     * Unregisters all JSP page servlets for a plugin.
     *
     * @param webXML the web.xml file containing JSP page names to servlet class file
     *      mappings.
     */
    public static void unregisterServlets(File webXML) {
        if (!webXML.exists()) {
            Log.error("Could not unregister plugin servlets, file " + webXML.getAbsolutePath() +
                    " does not exist.");
            return;
        }
        // Find the name of the plugin directory given that the webXML file
        // lives in plugins/[pluginName]/web/web.xml
        String pluginName = webXML.getParentFile().getParentFile().getName();
        try {
            SAXReader saxReader = new SAXReader();
            Document doc = saxReader.read(webXML);
            // Find all <servelt-mapping> entries to discover name to URL mapping.
            List names = doc.selectNodes("//servlet-mapping");
            for (int i=0; i<names.size(); i++) {
                Element nameElement = (Element)names.get(i);
                String url = nameElement.element("url-pattern").getTextTrim();
                // Destroy the servlet than remove from servlets map.
                HttpServlet servlet = servlets.get(pluginName + url);
                servlet.destroy();
                servlets.remove(pluginName + url);
                servlet = null;
            }
        }
        catch (Throwable e) {
            Log.error(e);
        }
    }

    /**
     * Handles a request for a JSP page. It checks to see if a servlet is mapped
     * for the JSP URL. If one is found, request handling is passed to it. If no
     * servlet is found, a 404 error is returned.
     *
     * @param pathInfo the extra path info.
     * @param request the request object.
     * @param response the response object.
     * @throws ServletException if a servlet exception occurs while handling the
     *      request.
     * @throws IOException if an IOException occurs while handling the request.
     */
    private void handleJSP(String pathInfo, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException
    {
        // Strip the starting "/" from the path to find the JSP URL.
        String jspURL = pathInfo.substring(1);
        HttpServlet servlet = servlets.get(jspURL);
        if (servlet != null) {
            servlet.service(request, response);
            return;
        }
        else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
    }

    /**
     * Handles a request for an image.
     *
     * @param pathInfo the extra path info.
     * @param response the response object.
     * @throws IOException if an IOException occurs while handling the request.
     */
    private void handleImage(String pathInfo, HttpServletResponse response) throws IOException
    {
        String [] parts = pathInfo.split("/");
        // Image request must be in correct format.
        if (parts.length != 4) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File image = new File(pluginDirectory, parts[1] + File.separator + "web" +
                File.separator + "images" + File.separator + parts[3]);
        if (!image.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        else {
            // Content type will be GIF or PNG.
            String contentType = "image/gif";
            if (pathInfo.endsWith(".png")) {
                contentType = "image/png";
            }
            response.setHeader("Content-disposition", "filename=\"" + image + "\";");
            response.setContentType(contentType);
            // Write out the image to the user.
            InputStream in = null;
            ServletOutputStream out = null;
            try {
                in = new BufferedInputStream(new FileInputStream(image));
                out = response.getOutputStream();

                // Set the size of the file.
                response.setContentLength((int)image.length());

                // Use a 1K buffer.
                byte[] buf = new byte[1024];
                int len;
                while ((len=in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            finally {
                try { in.close(); } catch (Exception ignored) {}
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }
}