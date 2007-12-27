/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2005, 2006 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.portlets;

import java.lang.Integer;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;

import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.portlet.GenericPortlet;
import javax.portlet.RenderRequest;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.RenderResponse;
import javax.portlet.PortletRequest;
import javax.portlet.PortletException;
import javax.portlet.PortletURL;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletContext;
import javax.portlet.PortletConfig;
import javax.portlet.WindowState;
import javax.portlet.PortletMode;
import javax.portlet.PortletSession;
import javax.portlet.ReadOnlyException;

import org.sakaiproject.portlet.util.VelocityHelper;
import org.sakaiproject.portlet.util.JSPHelper;
// import org.sakaiproject.portlet.util.FormattedText;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.ResourceLoader;

import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;

// Velocity
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.apache.velocity.app.VelocityEngine;

/**
 * a simple SakaiIFrame Portlet
 */
public class SakaiIFrame extends GenericPortlet {

    private static final Log LOG = LogFactory.getLog(SakaiIFrame.class);

    // This is old-style internationalization (i.e. not dynamic based
    // on user preference) to do that would make this depend on
    // Sakai Unique APIs. :(
    // private static ResourceBundle rb =  ResourceBundle.getBundle("iframe");
    protected static ResourceLoader rb = new ResourceLoader("iframe");

    protected final FormattedText validator = new FormattedText();

    private final VelocityHelper vHelper = new VelocityHelper();

    VelocityEngine vengine = null;

    private PortletContext pContext;

    // TODO: Perhaps these constancts should come from portlet.xml
    /** The source URL, in state, config and context. */
    protected final static String SOURCE = "sakai:source";

    /** The value in state and context for the source URL to actually used, as computed from special and URL. */
    protected final static String URL = "sakai:url";

    /** The height, in state, config and context. */
    protected final static String HEIGHT = "sakai:height";

    /** The custom height from user input * */
    protected final static String CUSTOM_HEIGHT = "sakai:customNumberField";

    public void init(PortletConfig config) throws PortletException {
        super.init(config);

        pContext = config.getPortletContext();
	try {
		vengine = vHelper.makeEngine(pContext);
	}
	catch(Exception e)
	{
		throw new PortletException("Cannot initialize Velocity ", e);
	}
	LOG.info("iFrame Portlet vengine="+vengine+" rb="+rb);
	
    }

    // Render the portlet - this is not supposed to change the state of the portlet
    // Render may be called many times so if it changes the state - that is tacky
    // Render will be called when someone presses "refresh" or when another portlet
    // onthe same page is handed an Action.
    public void doView(RenderRequest request, RenderResponse response)
            throws PortletException, IOException {
        response.setContentType("text/html");

	// System.out.println("==== doView called ====");

        PrintWriter out = response.getWriter();
        PortletSession pSession = request.getPortletSession(true);

 	PortletPreferences prefs = request.getPreferences();
        String source = prefs.getValue(SOURCE, null);
	if ( source == null ) source = prefs.getValue(URL,null);
        String height = prefs.getValue(HEIGHT, null);
	if ( height == null ) height = "1200px";

	// System.out.println("source="+source+" height="+height);

        if ( source != null ) {
            Context context = new VelocityContext();
            context.put("tlang", rb);
            context.put("validator", validator);
	    context.put("source",source);
	    context.put("height",height);
            // context.put("alertMessage",validator.escapeHtml("alert goes here"));
            vHelper.doTemplate(vengine, "/vm/main.vm", context, out);

	    Session session = SessionManager.getCurrentSession();
	    session.setAttribute("sakai-maximized-url",source);
System.out.println("Set sakai-maximized-url="+source);


        } else {
            out.println("Not yet configured");
        }

	// System.out.println("==== doView complete ====");
    }

    public void doEdit(RenderRequest request, RenderResponse response)
            throws PortletException, IOException {

        // System.out.println("==== doEdit called ====");
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        Context context = new VelocityContext();
        context.put("tlang", rb);
        context.put("validator", validator);

        // context.put("alertMessage",validator.escapeHtml("alert edit goes here"));

        PortletURL url = response.createActionURL();
	context.put("actionUrl", url.toString());
        context.put("doCancel", "sakai.cancel");
        context.put("doUpdate", "sakai.update");

 	PortletPreferences prefs = request.getPreferences();
        String source = prefs.getValue(SOURCE, null);
	if ( source == null ) source = prefs.getValue(URL,null);
	if ( source == null ) source = "";
	context.put("source",source);
        String height = prefs.getValue(HEIGHT, null);
	context.put("height",height);

        vHelper.doTemplate(vengine, "/vm/edit.vm", context, out);

        // System.out.println("==== doEdit done ====");
    }

    public void doHelp(RenderRequest request, RenderResponse response)
            throws PortletException, IOException {
        // System.out.println("==== doHelp called ====");
        // sendToJSP(request, response, "/help.jsp");
        JSPHelper.sendToJSP(pContext, request, response, "/help.jsp");
        // System.out.println("==== doHelp done ====");
    }

    // Process action is called for action URLs / form posts, etc
    // Process action is called once for each click - doView may be called many times
    // Hence an obsession in process action with putting things in session to 
    // Send to the render process.
    public void processAction(ActionRequest request, ActionResponse response)
            throws PortletException, IOException {

	// System.out.println("==== processAction called ====");

        PortletSession pSession = request.getPortletSession(true);

	// Our first challenge is to figure out which action we want to take
	// The view selects the "next action" either as a URL parameter
	// or as a hidden field in the POST data - we check both

        String doCancel = request.getParameter("sakai.cancel");
        String doUpdate = request.getParameter("sakai.update");

	// Our next challenge is to pick which action the previous view
	// has told us to do.  Note that the view may place several actions
	// on the screen and the user may have an option to pick between
	// them.  Make sure we handle the "no action" fall-through.

        pSession.removeAttribute("error.message");

	if ( doCancel != null ) {
		response.setPortletMode(PortletMode.VIEW);
        } else if ( doUpdate != null ) {
    		processActionEdit(request, response);
		response.setPortletMode(PortletMode.VIEW);
	} else {
		// System.out.println("Unknown action");
		response.setPortletMode(PortletMode.VIEW);
	}

	// System.out.println("==== End of ProcessAction  ====");
    }

    public void processActionEdit(ActionRequest request, ActionResponse response)
            throws PortletException, IOException 
    {
        // TODO: Check Role
        String title = request.getParameter("title");
        String height = request.getParameter("height");
        String source = request.getParameter("source");
        String cn = request.getParameter("customNumberField");

	// System.out.println("source="+source+" title="+title+" height="+height+" cn="+cn);

	if ( cn != null ) 
	{
		cn = cn.trim();
		if ( cn.length() > 1 && !cn.endsWith("px") ) 
		{
			cn += "px";
			height = cn;
		}
	}

        PortletSession pSession = request.getPortletSession(true);
        PortletPreferences prefs = request.getPreferences();
        try {
     		prefs.setValue(SOURCE, source);
     		prefs.setValue(HEIGHT, height);
                // System.out.println("Preference stored");

        } catch (ReadOnlyException e) {
		LOG.info("Unable to store preferences",e);
        }
        prefs.store();
    }

}
