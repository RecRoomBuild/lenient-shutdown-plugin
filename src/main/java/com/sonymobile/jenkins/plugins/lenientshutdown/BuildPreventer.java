/*
 *  The MIT License
 *
 *  Copyright (c) 2014 Sony Mobile Communications Inc. All rights reserved.
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

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sonymobile.jenkins.plugins.lenientshutdown.blockcauses.GlobalShutdownBlockage;
import com.sonymobile.jenkins.plugins.lenientshutdown.blockcauses.NodeShutdownBlockage;

import hudson.model.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.PlaceholderTask;

import hudson.Extension;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;

/**
 * Prevents builds from running when lenient shutdown mode is active.
 *
 * @author Fredrik Persson &lt;fredrik6.persson@sonymobile.com&gt;
 */
@Extension
public class BuildPreventer extends QueueTaskDispatcher {

    private static final Logger logger = Logger.getLogger(BuildPreventer.class.getName());

    /**
     * Handles prevention of builds for lenient shutdown on the Jenkins master.
     * @param item QueueItem to build
     * @return CauseOfBlockage if a build is prevented, otherwise null
     */
    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        ShutdownManageLink shutdownManageLink = ShutdownManageLink.getInstance();
        boolean isGoingToShutdown = shutdownManageLink.isGoingToShutdown();

        if (!isGoingToShutdown) {
            return null;
        }

        // PlaceholderTasks are part of already-running runs, and are allowed to go
        if (item.task instanceof PlaceholderTask) {
            return null;
        }

        CauseOfBlockage blockage = null; //Allow to run by default
        ShutdownConfiguration configuration = ShutdownConfiguration.getInstance();
        boolean isAllowListedProject = false;
        boolean isAllowListedUpStreamProject = false;

        long queueId = item.getId();

        // AbstractProjects and WorkflowJobs are checked to see if they can run
        if ((item.task instanceof AbstractProject || item.task instanceof WorkflowJob)
                && !shutdownManageLink.isPermittedQueueId(queueId)) {
            AbstractItem project = (AbstractItem)item.task;
            // By design, allow-listed projects are allowed to begin if there are other runs ongoing
            isAllowListedProject = shutdownManageLink.isActiveQueueIds()
                    && configuration.isAllowListedProject(project.getFullName());

            Set<Long> upstreamQueueIds = QueueUtils.getUpstreamQueueIds(item);
            boolean isPermittedByUpStream = shutdownManageLink.isAnyPermittedUpstreamProject(upstreamQueueIds);
            isAllowListedUpStreamProject = shutdownManageLink.isAnyAllowListedUpstreamProject(upstreamQueueIds);

            if (!isPermittedByUpStream && !isAllowListedProject && !isAllowListedUpStreamProject) {
                logger.log(Level.FINE, "Preventing project {0} from running, "
                        + "since lenient shutdown is active", item.task.getDisplayName());
                blockage = new GlobalShutdownBlockage();
            } else {
                if (isPermittedByUpStream) {
                    isAllowListedProject = false;
                }
            }
        }

        //Set the project as allowed upstream project if it was not blocked and shutdown enabled:
        if (blockage == null) {
            logger.log(Level.FINE, "Permitting {0} to start, even though lenient shutdown is pending",
                    item.task.getDisplayName());
            if (isAllowListedProject || isAllowListedUpStreamProject) {
                shutdownManageLink.addAllowListedQueueId(queueId);
            } else {
                shutdownManageLink.addPermittedUpstreamQueueId(queueId);
                shutdownManageLink.addActiveQueueId(queueId);
            }
        }

        return blockage;
    }

    /**
     * Handles prevention of builds specific for a node when taking specific nodes offline leniently.
     * @param node the node to check prevention for
     * @param item the buildable item to check prevention for
     * @return CauseOfBlockage if a build is prevented, otherwise null
     */
    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        CauseOfBlockage blockage = null; //Allow to run by default

        PluginImpl plugin = PluginImpl.getInstance();
        String nodeName = node.getNodeName();
        boolean nodeIsGoingToShutdown = plugin.isNodeShuttingDown(nodeName);

        if (!nodeIsGoingToShutdown) {
            return null;
        }

        long queueId = item.getId();
        if (item.task instanceof PlaceholderTask) {
            Run<?, ?> run = ((PlaceholderTask) item.task).run();
            if (run != null) {
                queueId = run.getQueueId();
            }
        }

        if (!plugin.isPermittedToRun(queueId, nodeName)) {
            blockage = new NodeShutdownBlockage();
            logger.log(Level.FINE, "Preventing task {0} from running on node {1}, "
                    + "since lenient shutdown is active on that node",
                    new String[] {
                            item.task.getDisplayName(),
                            nodeName,
            });
        } else {
            logger.log(Level.FINE, "Allowing task {0} to run on node {1}, "
                            + "even though node is shutting down",
                    new String[] {
                            item.task.getDisplayName(),
                            nodeName,
                    });
        }

        return blockage;
    }

}
