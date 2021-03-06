/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.xsdn.main.config.frm.impl;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;

import com.xsdn.main.config.frm.FlowNodeObserver;
import com.xsdn.main.config.frm.ForwardingRulesManager;
import com.xsdn.main.sw.SdnSwitchManager;
import com.xsdn.main.util.SimpleTaskRetryLooper;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeObserverImpl implements FlowNodeObserver {

    private static final Logger LOG = LoggerFactory.getLogger(FlowNodeObserverImpl.class);

    private final ForwardingRulesManager provider;

    private ListenerRegistration<DataChangeListener> listenerRegistration;

    public FlowNodeObserverImpl(final ForwardingRulesManager manager, final DataBroker db) {
        this.provider = Preconditions.checkNotNull(manager, "ForwardingRulesManager can not be null!");
        Preconditions.checkNotNull(db, "DataBroker can not be null!");
        /* Build Path */
        final InstanceIdentifier<FlowCapableNode> flowNodeWildCardIdentifier = InstanceIdentifier.create(Nodes.class)
                .child(Node.class).augmentation(FlowCapableNode.class);

        SimpleTaskRetryLooper looper = new SimpleTaskRetryLooper(ForwardingRulesManagerImpl.STARTUP_LOOP_TICK,
                ForwardingRulesManagerImpl.STARTUP_LOOP_MAX_RETRIES);
        try {
            listenerRegistration = looper.loopUntilNoException(new Callable<ListenerRegistration<DataChangeListener>>() {
                @Override
                public ListenerRegistration<DataChangeListener> call() throws Exception {
                    return db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                            flowNodeWildCardIdentifier, FlowNodeObserverImpl.this, DataChangeScope.BASE);
                }
            });
        } catch (Exception e) {
            LOG.warn("data listener registration failed: {}", e.getMessage());
            LOG.debug("data listener registration failed.. ", e);
            throw new IllegalStateException("FlowNodeObserver startup fail! System needs restart.", e);
        }
    }

    @Override
    public void close() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (Exception e) {
                LOG.warn("Error by stop FRM FlowNodeReconilListener: {}", e.getMessage());
                LOG.debug("Error by stop FRM FlowNodeReconilListener..", e);
            }
            listenerRegistration = null;
        }
    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent) {
        Preconditions.checkNotNull(changeEvent,"Async ChangeEvent can not be null!");
        /* All DataObjects for create */
        final Set<InstanceIdentifier<?>>  createdData = changeEvent.getCreatedData() != null
                ? changeEvent.getCreatedData().keySet() : Collections.<InstanceIdentifier<?>> emptySet();
        /* All DataObjects for remove */
        final Set<InstanceIdentifier<?>> removeData = changeEvent.getRemovedPaths() != null
                ? changeEvent.getRemovedPaths() : Collections.<InstanceIdentifier<?>> emptySet();

        for (InstanceIdentifier<?> entryKey : removeData) {
            final InstanceIdentifier<FlowCapableNode> nodeIdent = entryKey
                    .firstIdentifierOf(FlowCapableNode.class);
            if ( ! nodeIdent.isWildcarded()) {
                flowNodeDisconnected(nodeIdent);
            }
        }
        for (InstanceIdentifier<?> entryKey : createdData) {
            final InstanceIdentifier<FlowCapableNode> nodeIdent = entryKey
                    .firstIdentifierOf(FlowCapableNode.class);
            if ( ! nodeIdent.isWildcarded()) {
                flowNodeConnected(nodeIdent);
            }
        }
    }

    @Override
    public void flowNodeDisconnected(InstanceIdentifier<FlowCapableNode> disconnectedNode) {
        SdnSwitchManager.getSdnSwitchManager().switchDisconnected(disconnectedNode);
    }

    @Override
    public void flowNodeConnected(InstanceIdentifier<FlowCapableNode> connectedNode) {
        SdnSwitchManager.getSdnSwitchManager().switchConnected(connectedNode);
    }

/* Reconciliation will be done in the actor.
    private void reconciliation(final InstanceIdentifier<FlowCapableNode> nodeIdent) {

       ReadOnlyTransaction trans = provider.getReadTranaction();
        Optional<FlowCapableNode> flowNode = Optional.absent();

        try {
            flowNode = trans.read(LogicalDatastoreType.CONFIGURATION, nodeIdent).get();
        }
        catch (Exception e) {
            LOG.error("Fail with read Config/DS for Node {} !", nodeIdent, e);
        }

        if (flowNode.isPresent()) {
            *//* Tables - have to be pushed before groups *//*
            // CHECK if while pusing the update, updateTableInput can be null to emulate a table add
            List<Table> tableList = flowNode.get().getTable() != null
                    ? flowNode.get().getTable() : Collections.<Table> emptyList() ;
            for (Table table : tableList) {
                TableKey tableKey = table.getKey();
                KeyedInstanceIdentifier<TableFeatures, TableFeaturesKey> tableFeaturesII
                    = nodeIdent.child(Table.class, tableKey).child(TableFeatures.class, new TableFeaturesKey(tableKey.getId()));
                List<TableFeatures> tableFeatures = table.getTableFeatures();
                if (tableFeatures != null) {
                    for (TableFeatures tableFeaturesItem : tableFeatures) {
                        provider.getTableFeaturesCommiter().update(tableFeaturesII, tableFeaturesItem, null, nodeIdent);
                    }
                }
            }

            *//* Groups - have to be first *//*
            List<Group> groups = flowNode.get().getGroup() != null
                    ? flowNode.get().getGroup() : Collections.<Group> emptyList();
            for (Group group : groups) {
                final KeyedInstanceIdentifier<Group, GroupKey> groupIdent =
                        nodeIdent.child(Group.class, group.getKey());
                this.provider.getGroupCommiter().add(groupIdent, group, nodeIdent);
            }
            *//* Meters *//*
            List<Meter> meters = flowNode.get().getMeter() != null
                    ? flowNode.get().getMeter() : Collections.<Meter> emptyList();
            for (Meter meter : meters) {
                final KeyedInstanceIdentifier<Meter, MeterKey> meterIdent =
                        nodeIdent.child(Meter.class, meter.getKey());
                this.provider.getMeterCommiter().add(meterIdent, meter, nodeIdent);
            }
            *//* Flows *//*
            List<Table> tables = flowNode.get().getTable() != null
                    ? flowNode.get().getTable() : Collections.<Table> emptyList();
            for (Table table : tables) {
                final KeyedInstanceIdentifier<Table, TableKey> tableIdent =
                        nodeIdent.child(Table.class, table.getKey());
                List<Flow> flows = table.getFlow() != null ? table.getFlow() : Collections.<Flow> emptyList();
                for (Flow flow : flows) {
                    final KeyedInstanceIdentifier<Flow, FlowKey> flowIdent =
                            tableIdent.child(Flow.class, flow.getKey());
                    this.provider.getFlowCommiter().add(flowIdent, flow, nodeIdent);
                }
            }
        }
        *//* clean transaction *//*
        trans.close();
    }
    */
}

