/*
 *  The MIT License
 *
 *  Copyright (c) 2014 Sony Mobile Communications Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.PlaceholderTask;

/**
 * Utility class for getting information about the build queue and ongoing builds.
 *
 * @author Fredrik Persson &lt;fredrik6.persson@sonymobile.com&gt;
 */
public final class QueueUtils {

    private static final Logger logger = Logger.getLogger(QueueUtils.class.getName());

    /**
     * Hiding utility class constructor.
     */
    private QueueUtils() { }

    /**
     * Returns the set of queue ids for items that are in the build queue.
     * Depending on the configuration this is either just those that have a completed upstream
     * project if they are a project build,
     * all entries that have any upstream build,
     * or all entries that are currently in the queue.
     * Note: This method locks the queue; don't use excessively.
     * @return set of item ids
     */
    public static Set<Long> getPermittedQueueItemIds() {
        Set<Long> queuedIds = new HashSet<>();
        boolean allowAllQueuedItems = ShutdownConfiguration.getInstance().isAllowAllQueuedItems();
        boolean allowAllDownstreamItems = ShutdownConfiguration.getInstance().isAllowAllDownstreamItems();
        for (Queue.Item item : Queue.getInstance().getItems()) {
            long queueId = item.getId();
            if (item.task instanceof PlaceholderTask) {
                Run<?, ?> run = ((PlaceholderTask) item.task).run();
                if (run != null) {
                    queueId = run.getQueueId();
                }
            }

            if (item.task instanceof AbstractProject
                    || item.task instanceof WorkflowJob
                    || item.task instanceof PlaceholderTask) {
                if (allowAllQueuedItems) {
                    queuedIds.add(queueId);
                } else {
                    for (Run<?, ?> upstreamRun : getUpstreamBuilds(item)) {
                        if (!upstreamRun.isBuilding() || allowAllDownstreamItems) {
                            queuedIds.add(queueId);
                            break;
                        }
                    }
                }
            } else {
                logger.log(Level.FINE, "Unknown task found in queue: {0}", item.task.toString());
                queuedIds.add(queueId);
            }
        }
        return Collections.unmodifiableSet(queuedIds);
    }

    /**
     * Returns a set of queued item ids that are bound to a specific node
     * and should be permitted to build since they have a completed upstream project.
     * Note: This method locks the queue; don't use excessively.
     * @param nodeName the node name to check allowed ids for
     * @return set of permitted item ids
     */
    public static Set<Long> getPermittedQueueItemIds(String nodeName) {
        Set<Long> permittedQueueItemIds = new HashSet<Long>();
        if (nodeName == null) {
            permittedQueueItemIds.addAll(getPermittedQueueItemIds());
        } else {
            Queue queueInstance = Queue.getInstance();

            Node node = Jenkins.get().getNode(nodeName);
            if (nodeName.isEmpty()) { // Special case when building on master
                node = Jenkins.get();
            }

            if (node != null) {
                for (long id : getPermittedQueueItemIds()) {
                    Queue.Item item = queueInstance.getItem(id);
                    if (item != null && !canOtherNodeBuild(item, node)) {
                        permittedQueueItemIds.add(id);
                    }
                }
            }
        }

        return Collections.unmodifiableSet(permittedQueueItemIds);
    }

    /**
     * Return a set of queue ids of all currently running builds.
     *
     * @return set of running queue ids
     */
    public static Set<Long> getRunningProjectQueueIds() {
        Set<Long> runningProjects = new HashSet<>();

        List<Node> allNodes = new ArrayList<>(Jenkins.get().getNodes());
        allNodes.add(Jenkins.get());

        for (Node node : allNodes) {
            runningProjects.addAll(getRunningProjectsQueueIDs(node.getNodeName()));
        }
        return Collections.unmodifiableSet(runningProjects);
    }

    /**
     * Old, misspelled method name. Use getRunningProjectsQueueIDs instead.
     */
    @Deprecated
    public static Set<Long> getRunninProjectsQueueIDs(String nodeName) {
        return getRunningProjectsQueueIDs(nodeName);
    }

    /**
     * Returns a set of queue ids of all currently running builds on a node.
     * For pipeline builds, only the queue id of the WorkflowRun is included.
     *
     * @param nodeName the node name to list running projects for
     * @return set of queue ids
     */
    public static Set<Long> getRunningProjectsQueueIDs(String nodeName) {
        Set<Long> runningProjects = new HashSet<>();

        Node node = Jenkins.get().getNode(nodeName);
        if (nodeName.isEmpty()) { // Special case when building on master
            node = Jenkins.get();
        }

        if (node != null) {
            Computer computer = node.toComputer();
            if (computer != null) {
                List<Executor> executors = computer.getAllExecutors();

                for (Executor executor : executors) {
                    Queue.Executable executable = executor.getCurrentExecutable();
                    if (executable instanceof Run) {
                        // Handles both AbstractBuild and WorkflowRun
                        Run<?, ?> run = (Run<?, ?>)executable;
                        runningProjects.add(run.getQueueId());
                    } else if (executable != null && executable.getParent() instanceof PlaceholderTask) {
                        // Handles PlaceholderTask
                        Run<?, ?> run = ((PlaceholderTask) executable.getParent()).run();
                        if (run != null) {
                            runningProjects.add(run.getQueueId());
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableSet(runningProjects);
    }

    /**
     * Gets the queue ids of all upstream projects that triggered argument queue item.
     * @param item the queue item to find upstream projects for
     * @return set of upstream project names
     */
    public static Set<Long> getUpstreamQueueIds(Queue.Item item) {
        Set<Run<?, ?>> upstreamRuns = getUpstreamBuildsInternal(item);
        return Collections.unmodifiableSet(
                upstreamRuns.stream().map(Run::getQueueId).collect(Collectors.toSet())
        );
    }

    /**
     * Gets all upstream builds that triggered argument queue item.
     * @param item the queue item to find upstream builds for
     * @return set of upstream builds
     */
    public static Set<Run<?, ?>> getUpstreamBuilds(Queue.Item item) {
        return Collections.unmodifiableSet(getUpstreamBuildsInternal(item));
    }

    private static Set<Run<?, ?>> getUpstreamBuildsInternal(Queue.Item item) {
        Set<Run<?, ?>> upstreamBuilds = new HashSet<>();
        List<Cause> causes;
        if (item.task instanceof PlaceholderTask) {
            Run<?, ?> run = ((PlaceholderTask) item.task).run();
            causes = run != null ? run.getCauses() : null;
        } else {
            causes = item.getCauses();
        }

        if (causes != null) {
            for (Cause cause : causes) {
                if (cause instanceof Cause.UpstreamCause) {
                    Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
                    Run<?, ?> upstreamRun = upstreamCause.getUpstreamRun();

                    if (upstreamRun != null) {
                        upstreamBuilds.add(upstreamRun);
                    }
                } else if (cause instanceof Cause.UserIdCause) {
                    // This was *actually* started by a user! (Probably via rebuild) - Don't report any upstream runs
                    return Collections.emptySet();
                }
            }
        }
        return upstreamBuilds;
    }

    /**
     * Checks if there are any online nodes other than the argument node
     * that can build the item.
     * @param item the item to build
     * @param node the node to exclude in the search
     * @return true if any other available nodes were found, otherwise false
     */
    public static boolean canOtherNodeBuild(Queue.Item item, Node node) {
        if (item instanceof Queue.BuildableItem) {
            return canOtherNodeBuild((Queue.BuildableItem)item, node);
        } else if (item instanceof Queue.WaitingItem) {
            Queue.BuildableItem hypotheticalBuildable = new Queue.BuildableItem((Queue.WaitingItem)item);
            return canOtherNodeBuild(hypotheticalBuildable, node);
        }
        return false;
    }

    private static boolean canOtherNodeBuild(Queue.BuildableItem item, Node node) {
        Set<Node> allNodes = new HashSet<>(Jenkins.get().getNodes());
        allNodes.add(Jenkins.get());

        for (Node otherNode : allNodes) {
            Computer otherComputer = otherNode.toComputer();
            if (otherComputer != null && otherComputer.isOnline() && !otherNode.equals(node)
                    && otherNode.canTake(item) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if argument computer is currently building something.
     * @param computer the computer to check for
     * @return true if computer is building, otherwise false
     */
    public static boolean isBuilding(Computer computer) {
        boolean isBuilding = false;
        List<Executor> executors = computer.getAllExecutors();

        for (Executor executor : executors) {
            if (executor.isBusy()) {
                isBuilding = true;
                break;
            }
        }

        return isBuilding;
    }

    /**
     * Checks if there are any builds in queue that can only be built
     * by the argument computer.
     * Note: This method locks the queue; don't use excessively.
     * @param computer the computer to check assignment for
     * @return true if there are builds that can only be build by argument computer, otherwise false
     */
    public static boolean hasNodeExclusiveItemInQueue(Computer computer) {
        boolean hasExclusive = false;
        Queue.Item[] queueItems = Queue.getInstance().getItems();

        for (Queue.Item item : queueItems) {
            if (!canOtherNodeBuild(item, computer.getNode())) {
                hasExclusive = true;
                break;
            }
        }
        return hasExclusive;
    }
}
