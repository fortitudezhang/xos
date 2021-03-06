package com.xsdn.main.sw;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Props;
import com.google.common.collect.Sets;
import com.xsdn.main.util.OFutils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.com.xsdn.xos.params.xml.ns.yang.xos.rev150820.xos.ai.active.passive.switchset.east.EastSwitch;
import org.opendaylight.yang.gen.v1.urn.com.xsdn.xos.params.xml.ns.yang.xos.rev150820.xos.ai.active.passive.switchset.west.WestSwitch;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.SalGroupService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import com.xsdn.main.sw.SdnSwitchActor;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by fortitude on 15-9-6.
 */
public class SdnSwitchManager {
    private static final Logger LOG = LoggerFactory.getLogger(SdnSwitchManager.class);
    private static SdnSwitchManager sdnSwitchManager = null;
    private boolean inited = false;

    NodeId westSdnSwitchNodeId = null;
    NodeId eastSdnSwitchNodeId = null;

    HashMap<String, NodeId> connectedSwitches = new HashMap();

    private ActorRef westActorRef;
    private ActorRef eastActorRef;

    public static SdnSwitchManager getSdnSwitchManager() {
        if (null == sdnSwitchManager) {
            SdnSwitchManager.sdnSwitchManager = new SdnSwitchManager();
        }

        return sdnSwitchManager;
    }

    public void init(ActorSystem system, PacketProcessingService packetProcessingService,
                     SalFlowService salFlowService, SalGroupService salGroupService,
                     DataBroker dataService) {
        this.westActorRef = system.actorOf(SdnSwitchActor.props(packetProcessingService, salFlowService, salGroupService, dataService));
        this.eastActorRef = system.actorOf(SdnSwitchActor.props(packetProcessingService, salFlowService, salGroupService, dataService));

        /* Send periodical arp probe message to trigger arp probe and mac learning.
         * TODO: let 50ms to be configurable. */
        SdnSwitchActor.ProbeArpOnce probeArpOnce = new SdnSwitchActor.ProbeArpOnce();
        Cancellable _cl1 =  system.scheduler().schedule(Duration.Zero(),
                Duration.create(30000, TimeUnit.MILLISECONDS), westActorRef, probeArpOnce, system.dispatcher(), null);
        Cancellable _cl2 =  system.scheduler().schedule(Duration.Zero(),
                Duration.create(30000, TimeUnit.MILLISECONDS), eastActorRef, probeArpOnce, system.dispatcher(), null);

        inited = true;
    }

    public ActorRef getWestSdnSwitchActor() {
        return westActorRef;
    }

    public ActorRef getEastSdnSwitchActor() {
        return eastActorRef;
    }

    public void updateWestSdnSwitch(WestSwitch westSwitch) {
        ActorRef westSwitchActor = null;
        String dpid = westSwitch.getDpid();

        // TODO: check how to handle dpid delete?? whether we need to support it ??
        // if we support change dpid, we must emulate switch disconnect event to the actor
        // to let the actor clean it's own data.

        if (!dpid.equals("")) {
            // TODO: we currently not support update dpid (include delete it)once it's set.
            if (westSdnSwitchNodeId != null) {
                westSwitchActor = getSdnSwitchByNodeId(westSdnSwitchNodeId);
            }
            else {
                try {
                    String nodeIdUri = OFutils.BuildNodeIdUriByDpid(dpid);
                    westSdnSwitchNodeId = new NodeId(nodeIdUri);
                } catch (NumberFormatException e) {
                    LOG.error("Configured dpid " + dpid + " is invalid");
                    westSdnSwitchNodeId = null;
                }

                if (null != westSdnSwitchNodeId) {
                    westSwitchActor = getSdnSwitchByNodeId(westSdnSwitchNodeId);
                    westSwitchActor.tell(new SdnSwitchActor.DpIdCreated(dpid), null);

                    // Notify connected event if dpid is configured later.
                    if (connectedSwitches.containsKey(westSdnSwitchNodeId.getValue())) {
                        westSwitchActor.tell(new SdnSwitchActor.SwitchConnected(), null);
                    }
                }
            }
        }
        else {
            // TODO: dpid is invalid, nothing to do currently.
            return;
        }

        // Do other stuffs the switch can be updated, such as edge-router-interface-ip.
        if (westSwitchActor != null) {

            /* Since we subscribe in a switch granularity, we simply send the message the switch actor,
            * let it to check whether there is a change in edge-router-interface-ip or other switch related data. */
            if (null != westSwitch.getVirtualGatewayMac()) {
                westSwitchActor.tell(
                        new SdnSwitchActor.VirtualGatewayMacUpdate(westSwitch.getVirtualGatewayMac()),
                        null);
            }
            if (null != westSwitch.getQuaggaInterfaceIp()) {
                westSwitchActor.tell(
                        new SdnSwitchActor.QuaggaInterfaceIpUpdate(westSwitch.getQuaggaInterfaceIp()),
                        null);
            }
            if (null != westSwitch.getEdgeRouterInterfaceIp()) {
                westSwitchActor.tell(
                        new SdnSwitchActor.EdgeRouterInterfaceIpUpdate(westSwitch.getEdgeRouterInterfaceIp()),
                        null);
            }
         }
    }

    public void updateEastSdnSwitch(EastSwitch eastSwitch) {
        ActorRef eastSwitchActor = null;
        String dpid = eastSwitch.getDpid();

        // TODO: check how to handle dpid delete?? whether we need to support it ??
        // if we support change dpid, we must emulate switch disconnect event to the actor
        // to let the actor clean it's own data.

        if (!dpid.equals("")) {
            // TODO: we currently not support update dpid (include delete it)once it's set.
            if (eastSdnSwitchNodeId != null) {
                eastSwitchActor = getSdnSwitchByNodeId(eastSdnSwitchNodeId);
            }
            else {
                try {
                    String nodeIdUri = OFutils.BuildNodeIdUriByDpid(dpid);
                    eastSdnSwitchNodeId = new NodeId(nodeIdUri);
                } catch (NumberFormatException e) {
                    LOG.error("Configured dpid " + dpid + " is invalid");
                    eastSdnSwitchNodeId = null;
                }

                if (null != eastSdnSwitchNodeId) {
                    eastSwitchActor = getSdnSwitchByNodeId(eastSdnSwitchNodeId);
                    eastSwitchActor.tell(new SdnSwitchActor.DpIdCreated(dpid), null);

                    // Notify connected event if dpid is configured later.
                    if (connectedSwitches.containsKey(eastSdnSwitchNodeId.getValue())) {
                        eastSwitchActor.tell(new SdnSwitchActor.SwitchConnected(), null);
                    }
                }
            }
        }
        else {
            // TODO: dpid is invalid, nothing to do currently.
            return;
        }

        // Do other stuffs the switch can be updated, such as edge-router-interface-ip.
        if (eastSwitchActor != null) {

            /* Since we subscribe in a switch granularity, we simply send the message the switch actor,
            * let it to check whether there is a change in edge-router-interface-ip or other switch related data. */
            if (null != eastSwitch.getVirtualGatewayMac()) {
                eastSwitchActor.tell(
                        new SdnSwitchActor.VirtualGatewayMacUpdate(eastSwitch.getVirtualGatewayMac()),
                        null);
            }
            if (null != eastSwitch.getQuaggaInterfaceIp()) {
                eastSwitchActor.tell(
                        new SdnSwitchActor.QuaggaInterfaceIpUpdate(eastSwitch.getQuaggaInterfaceIp()),
                        null);
            }
            if (null != eastSwitch.getEdgeRouterInterfaceIp()) {
                eastSwitchActor.tell(
                        new SdnSwitchActor.EdgeRouterInterfaceIpUpdate(eastSwitch.getEdgeRouterInterfaceIp()),
                        null);
            }
        }
    }

    public ActorRef getSdnSwitchByNodeId(NodeId nodeId) {
        if (nodeId.equals(westSdnSwitchNodeId)) {
            return westActorRef;
        }

        if (nodeId.equals(eastSdnSwitchNodeId)) {
            return eastActorRef;
        }

        return null;
    }

    public boolean isWest(NodeId nodeId) {
        if (nodeId.equals(westSdnSwitchNodeId)) {
            return true;
        }

        return false;
    }

    public boolean isEast(NodeId nodeId) {
        if (nodeId.equals(eastSdnSwitchNodeId)) {
            return true;
        }

        return false;
    }

    public void notifyAppStatus(int status) {
        // TODO: handle message deliver fail??? seems actor in the same JVM instance will
        // be delivered surely.
        westActorRef.tell(new SdnSwitchActor.AppStatusUpdate(status), null);
        eastActorRef.tell(new SdnSwitchActor.AppStatusUpdate(status), null);
    }

    public void switchConnected(InstanceIdentifier<FlowCapableNode> connectedNode) {
        NodeId nodeId;

        nodeId = connectedNode.firstIdentifierOf(Node.class).firstKeyOf(Node.class, NodeKey.class).getId();

        connectedSwitches.put(nodeId.getValue(), nodeId);

        if (null != getSdnSwitchByNodeId(nodeId)) {
            getSdnSwitchByNodeId(nodeId).tell(new SdnSwitchActor.SwitchConnected(), null);
        }

    }

    public void switchDisconnected(InstanceIdentifier<FlowCapableNode> connectedNode) {
        NodeId nodeId;

        nodeId = connectedNode.firstIdentifierOf(Node.class).firstKeyOf(Node.class, NodeKey.class).getId();

        if (null != getSdnSwitchByNodeId(nodeId)) {
            getSdnSwitchByNodeId(nodeId).tell(new SdnSwitchActor.SwitchDisconnected(), null);
        }

        connectedSwitches.remove(nodeId.getValue());
    }
}
