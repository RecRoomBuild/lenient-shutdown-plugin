package com.sonymobile.jenkins.plugins.lenientshutdown;

import hudson.Extension;
import hudson.model.Node;
import jenkins.model.NodeListener;

import javax.annotation.Nonnull;

@Extension
public class ShutdownNodeListener extends NodeListener {
    @Override
    protected void onDeleted(@Nonnull Node node) {
        super.onDeleted(node);
        final PluginImpl plugin = PluginImpl.getInstance();
        if (plugin.isNodeShuttingDown(node.getNodeName())) {
            plugin.cancelNodeShuttingDown(node.getNodeName());
        }
    }
}
