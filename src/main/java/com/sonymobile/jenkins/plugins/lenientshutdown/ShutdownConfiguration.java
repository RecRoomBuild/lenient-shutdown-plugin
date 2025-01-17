/*
 *  The MIT License
 *
 *  Copyright (c) 2016 Markus Winter. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.sonymobile.jenkins.plugins.lenientshutdown;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

/**
 * The global configuration of the plugin.
 *
 */
@Extension
public class ShutdownConfiguration extends GlobalConfiguration {

    private static final String DELIMETER = "\\r?\\n";

    /**
     * Defines the default shutdown message to be displayed in header.
     */
    private String shutdownMessage = Messages.GoingToShutDown();

    private boolean allowAllQueuedItems;

    private boolean allowAllDownstreamItems;

    private boolean allowAllowListedProjects;

    /**
     * A list of projects that are allowed to run in case allowAllowListedProjects is enabled.
     */
    private final Set<String> allowListedProjects = Collections.synchronizedSet(new TreeSet<>());

    /**
     * Constructor, loads persisted configuration.
     */
    public ShutdownConfiguration() {
        load();
    }

    /**
     * Checks if all queued items are allowed to build in lenient shutdown mode.
     *
     * @return true if all queued items will build, false otherwise
     */
    public boolean isAllowAllQueuedItems() {
        return allowAllQueuedItems;
    }

    /**
     * Checks if descendants of all upstream jobs (as opposed to just completed ones) are allowed to build in
     * lenient shutdown mode.
     *
     * @return true is downstream items of all started upstream jobs will build, false otherwise
     */
    public boolean isAllowAllDownstreamItems() {
        return allowAllDownstreamItems;
    }

    /**
     * Checks if allow-listed projects are allowed to build in lenient shutdown mode.
     *
     * @return true if allow-listed projects will build, false otherwise
     */
    public boolean isAllowAllowListedProjects() {
        return allowAllowListedProjects;
    }

    /**
     * Sets the flag if all queued items are allowed to finish or not.
     *
     * @param allowAllQueuedItems true - enabled, false - disabled
     */
    public void setAllowAllQueuedItems(boolean allowAllQueuedItems) {
        this.allowAllQueuedItems = allowAllQueuedItems;
    }

    /**
     * Sets the flag if descendants of all upstream jobs are allowed to build in lenient shutdown mode.
     *
     * @param allowAllDownstreamItems true - enabled, false - disabled
     */
    public void setAllowAllDownstreamItems(boolean allowAllDownstreamItems) {
        this.allowAllDownstreamItems = allowAllDownstreamItems;
    }

    /**
     * Sets the flag if allow-listed projects are allowed or not.
     *
     * @param allowAllowListedProjects true - enabled, false - disabled
     */
    public void setAllowAllowListedProjects(boolean allowAllowListedProjects) {
        this.allowAllowListedProjects = allowAllowListedProjects;
    }

    /**
     * Gets the shutdown message to be displayed in header.
     *
     * @return message to display in header
     */
    public String getShutdownMessage() {
        return shutdownMessage;
    }

    /**
     * Sets the shutdown message to be displayed in header.
     *
     * @param shutdownMessage message to display in header
     */
    public void setShutdownMessage(String shutdownMessage) {
        this.shutdownMessage = shutdownMessage;
    }

    /**
     * Gets the allow-listed projects as a string.
     *
     * @return string with the allow-listed projects separated by newlines
     */
    public String getAllowListedProjectsText() {
        return StringUtils.join(allowListedProjects, "\n");
    }

    /**
     * Gets the set of allow-listed projects.
     *
     * @return Set of allow-listed projects
     */
    public Set<String> getAllowListedProjects() {
        return allowListedProjects;
    }

    /**
     * Checks whether the given project name is allowed to run.
     *
     * @param name the name of the project to check
     * @return true if allow-listed projects are allowed and the given project name is found in the list
     */
    public boolean isAllowListedProject(String name) {
        return allowAllowListedProjects && allowListedProjects.contains(name);
    }

    /**
     * Called when an admin saves settings in the global configuration page.
     * Persists the current settings to disk.
     *
     * @param staplerRequest the request
     * @param json form data
     * @return always true
     * @throws FormException if the form was malformed
     */
    @Override
    public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException {
        shutdownMessage = json.getString("shutdownMessage");
        allowAllQueuedItems = json.getBoolean("allowAllQueuedItems");
        allowAllDownstreamItems = json.getBoolean("allowAllDownstreamItems");
        allowAllowListedProjects = json.getBoolean("allowAllowListedProjects");
        allowListedProjects.clear();
        allowListedProjects.addAll(Arrays.asList(json.getString("allowListedProjects").split(DELIMETER)));
        save();
        return true;
    }

    /**
     * The singleton instance registered in the Jenkins extension list.
     *
     * @return the instance
     */
    public static ShutdownConfiguration getInstance() {
        return GlobalConfiguration.all().get(ShutdownConfiguration.class);
    }
}
