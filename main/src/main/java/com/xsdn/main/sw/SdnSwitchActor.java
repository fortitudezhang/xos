package com.xsdn.main.sw;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.xsdn.main.ha.XosAppStatusMgr;
import com.xsdn.main.packet.ArpPacketBuilder;
import com.xsdn.main.util.EtherAddress;
import com.xsdn.main.util.Ip4Network;
import com.xsdn.main.util.OFutils;
import com.xsdn.main.util.Constants;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetDlDstActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetDlSrcActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.dl.dst.action._case.SetDlDstActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.dl.src.action._case.SetDlSrcActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.common.action.rev150203.action.grouping.action.choice.set.field._case.SetFieldActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.KnownOperation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.arp.packet.received.packet.chain.packet.ArpPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.KnownEtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.rev150820.sdn._switch.UserFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.rev150820.xos.ai.active.passive.switchset.AiManagedSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.rev150820.managed.subnet.SubnetHost;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Created by fortitude on 15-8-23.
 */
public class SdnSwitchActor extends UntypedActor {
    private static final Logger LOG = LoggerFactory.getLogger(SdnSwitchActor.class);
    private static final HashMap<Short, AiManagedSubnet> subnetMap = new HashMap(50); // TODO: 50 is a arbitrary number now.
    private PacketProcessingService packetProcessingService = null;
    private SalFlowService salFlowService = null;
    private DataBroker dataService = null;
    private String dpid;
    private NodeId nodeId;
    private int appStatus = XosAppStatusMgr.APP_STATUS_INVALID;
    private boolean deviceConnected = false;
    private MacAddress vGMAC = new MacAddress(Constants.INVALID_MAC_ADDRESS);
    private Ipv4Address edgeRouterInterfaceIp = new Ipv4Address("255.255.255.255");
    private Ipv4Address quaggaInterfaceIp = new Ipv4Address("255.255.255.255");

    private OFpluginHelper ofpluginHelper = null;
    private MdsalHelper mdsalHelper = null;

    // Note: WZJ, for store host ip/mac mapping
    private ConcurrentMap<Short, List> subnetTracer = new ConcurrentHashMap<>();
    private ConcurrentMap<String, HostInfo> hostTracer = new ConcurrentHashMap<>();

    private SdnSwitchActor(final PacketProcessingService packetProcessingService,
                           final SalFlowService salFlowService,
                           final DataBroker dataService) {
        this.packetProcessingService = Preconditions.checkNotNull(packetProcessingService);
        this.salFlowService = salFlowService;
        this.dataService = dataService;
        this.ofpluginHelper = new OFpluginHelper(salFlowService);
        this.mdsalHelper = new MdsalHelper(dataService);
    }

    // Define messages which will be processed by this actor.
    static public class DpIdCreated {
        private final String dpId;

        public DpIdCreated(String dpId) {
            this.dpId = dpId;
        }

        public String getDpId() {
            return dpId;
        }
    }

    // The two message will be used to implement reconciliation logic by which
    // we will re-program the flow tables if the switch have rebooted or reconnect.
    static public class SwitchConnected {
        public SwitchConnected() {
        }
    }

    static public class SwitchDisconnected {
        public SwitchDisconnected() {
        }
    }

    static public class AppStatusUpdate {
        private int appStatus = XosAppStatusMgr.APP_STATUS_INVALID;

        public AppStatusUpdate(int status) {
            this.appStatus = status;
        }
    }

    static public class ProbeArpOnce {
        public ProbeArpOnce() {

        }
    }

    static public class ManagedSubnetUpdate {
        private AiManagedSubnet subnet;
        boolean delete;

        public ManagedSubnetUpdate(AiManagedSubnet subnet, boolean delete) {
            this.subnet = subnet;
            this.delete = delete;
        }
    }

    static public class ArpPacketIn {
        private NodeId nodeId;
        private RawPacket rawPkt;
        private ArpPacket pkt;

        public ArpPacketIn(NodeId nodeId, RawPacket rawPkt, ArpPacket pkt) {
            // Record node id for possible usage later.
            this.nodeId = nodeId;
            this.rawPkt = rawPkt;
            this.pkt = pkt;
        }
    }

    static public class UserFlowOp {
        private short op;
        private UserFlow userFlow;

        public UserFlowOp(short op, UserFlow userFlow) {
            this.op = op;
            this.userFlow = userFlow;
        }
    }

    static public class VirtualGatewayMacUpdate {
        private MacAddress address;

        public VirtualGatewayMacUpdate(MacAddress address) {
            this.address = address;
        }
    }


    static public class QuaggaInterfaceIpUpdate {
        private Ipv4Address address;

        public QuaggaInterfaceIpUpdate(Ipv4Address address) {
            this.address = address;
        }
    }


    static public class EdgeRouterInterfaceIpUpdate {
        private Ipv4Address address;

        public EdgeRouterInterfaceIpUpdate(Ipv4Address address) {
            this.address = address;
        }
    }

    public SdnSwitchActor() {
        // TODO:
        // 1. initialize runtime database
        // 2. start arp prober thread
        // 3. provide callback for extern events like pkt in
        // 4. implement master-slave decide logic
    }

    /**
     * Note: WZJ, Note: WZJ, Store the host mac and ingress
     */
    public class HostInfo {
        private String mac;
        private NodeConnectorRef ingress;

        public HostInfo(String mac, NodeConnectorRef ingress) {
            this.mac = mac;
            this.ingress = ingress;
        }

        public NodeConnectorRef getIngress() {
            return this.ingress;
        }

        public String getMac() {
            return this.mac;
        }
    }

    /**
     * Note: WZJ, add l2 forward flows
     */
    private void addL2ForwardFlows(MacAddress destMac, NodeConnectorRef destPort) {
        if (destMac == null) {
            return;
        }

        // Match.
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetDestination(new EthernetDestinationBuilder().setAddress(destMac).build());
        MatchBuilder matchBuilder = new MatchBuilder().setEthernetMatch(ethernetMatchBuilder.build());

        // Actions.
        Uri destPortUri = destPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
        ActionBuilder actionBuilder = new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(destPortUri)
                                .build())
                        .build());
        org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match match = matchBuilder.build();
        List<Action> actions = new ArrayList<Action>();
        actions.add(actionBuilder.build());
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actions).build();
        InstructionBuilder applyActionsInstructionBuilder = new InstructionBuilder()
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()
                        .setApplyActions(applyActions)
                        .build());
        InstructionsBuilder instructionsBuilder = new InstructionsBuilder() //
                .setInstruction(ImmutableList.of(applyActionsInstructionBuilder.build()));

        this.ofpluginHelper.addFlow(this.dpid, Constants.XOS_L2_FORWARD_FLOW_NAME,
                Constants.XOS_L2_FORWARD_FLOW_PRIORITY,
                matchBuilder.build(), instructionsBuilder.build());

        // Action 2: store to our md sal datastore.
        this.mdsalHelper.storeAppFlow(this.nodeId, Constants.XOS_APP_DFT_ARP_FLOW_NAME,
                matchBuilder.build(), instructionsBuilder.build());

        LOG.info("Pushed l2 flow {} to the switch {}", "_XOS_L2_FORWARD", this.dpid);
    }

    private void addDftArpFlows() {
        // Match.
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(KnownEtherType.Arp.getIntValue()))).build());
        MatchBuilder matchBuilder = new MatchBuilder().setEthernetMatch(ethernetMatchBuilder.build());


        // Actions.
        ActionBuilder actionBuilder = new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                .build())
                        .build());
        List<Action> actions = new ArrayList<Action>();
        actions.add(actionBuilder.build());
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actions).build();
        InstructionBuilder applyActionsInstructionBuilder = new InstructionBuilder()
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()
                        .setApplyActions(applyActions)
                        .build());
        InstructionsBuilder instructionsBuilder = new InstructionsBuilder() //
                .setInstruction(ImmutableList.of(applyActionsInstructionBuilder.build()));

        this.ofpluginHelper.addFlow(this.dpid, Constants.XOS_APP_DFT_ARP_FLOW_NAME,
                Constants.XOS_APP_DFT_ARP_FLOW_PRIORITY,
                matchBuilder.build(), instructionsBuilder.build());

        // Note: we need install default arp flow for both active and backup switch.

        // Action 2: store to our md sal datastore.

        this.mdsalHelper.storeAppFlow(this.nodeId, Constants.XOS_APP_DFT_ARP_FLOW_NAME,
                matchBuilder.build(), instructionsBuilder.build());

        LOG.info("Pushed init flow {} to the switch {}", Constants.XOS_APP_DFT_ARP_FLOW_NAME, this.dpid);
    }

    private void addInitFlows() {
        if (!deviceConnected) {
            LOG.info("Device is not connected, skip init flow provisioning");
            return;
        }

        addDftArpFlows();
    }

    private void processAppStatusUpdate(int status) {
        // Dpid is not set yet, just record app status.
        if (null == this.dpid) {
            this.appStatus = status;
            return;
        }

        if (this.appStatus != status) {
            if (status == XosAppStatusMgr.APP_STATUS_ACTIVE) {
                // We are now running active controller.
                // There will be quite complicate logic, may be we should spawn a seperate actor to handle all
                // the sub task.
                // Basically we need handle the following tasks:
                // 1. INVALID->ACTIVE
                //    1.1 init state, clear all flow of the managed switch.
                //    1.2 update controller role to master instead of equal.
                //    1.2 install default flow and store the flow in to the xos operati.
                // 2. BACKUP->ACTIVE
                //    2.1
                // 3. ACTIVE->BACKUP
                //    3.1 ... TBD

                // Case 1, INVALID->ACTIVE.
                if (this.appStatus == XosAppStatusMgr.APP_STATUS_INVALID) {
                    this.addInitFlows();
                }
            }
            this.appStatus = status;

            LOG.info("Update sdn switch actor to status {}", this.appStatus);
        } else {
            LOG.error("Duplicate status recevied {} for sdnswitch actor {}", this.appStatus, this.dpid);
        }
    }

    private void processDpid(String dpid) {
        LOG.info("SdnSwitch actor received dpid created, dpid is " + dpid);
        if (null == this.dpid) {
            this.dpid = dpid;
            this.nodeId = new NodeId(OFutils.BuildNodeIdUriByDpid(this.dpid));
            if (this.appStatus == XosAppStatusMgr.APP_STATUS_ACTIVE) {
                // TODO: init the switch since we have the dpid configured now.
                this.addInitFlows();
            }
        } else {
            // TODO: handle update.
        }
    }

    private void processSwitchConnected() {
        LOG.info("SdnSwitch actor received switch connected event, dpid is " + dpid);

        this.deviceConnected = true;

        // TODO: do the reconciliation logic, the code here is just a test to do the event driven logic.
        if ((null != this.dpid) && (this.appStatus == XosAppStatusMgr.APP_STATUS_ACTIVE)) {
            this.addInitFlows();
        }
    }

    private void processSwitchDisonnected() {
        LOG.info("SdnSwitch actor received switch disconnected event, dpid is " + dpid);

        this.deviceConnected = false;
    }

    private void processSubnetUpdate(ManagedSubnetUpdate subnetUpdate) {
        // TODO: this code need to be refactored because I only want to extract the subnet information
        // more santity check need to be done.
        // And also, we should build a auxiliary map that use the virtual gateway ip as index to help
        // do the arp proxy.
        // We should not try to read data from the data store directly because the transaction read is slow.
        if (!subnetUpdate.delete) {
            this.subnetMap.put(subnetUpdate.subnet.getKey().getSubnetId(), subnetUpdate.subnet);
            /* Note: WZJ, add or update host info */
            handleHostUpdate(subnetUpdate.subnet.getKey().getSubnetId(), subnetUpdate.subnet, false);
        } else {
            this.subnetMap.remove(subnetUpdate.subnet.getKey().getSubnetId());
            this.subnetMap.put(subnetUpdate.subnet.getKey().getSubnetId(), subnetUpdate.subnet);
            /* Note: WZJ, add or update host info */
            handleHostUpdate(subnetUpdate.subnet.getKey().getSubnetId(), subnetUpdate.subnet, true);
        }
    }

    private boolean processArpReqForVGW(ArpPacketIn pktIn) {
        String dip = pktIn.pkt.getDestinationProtocolAddress();
        Ipv4Address dIPv4 = new Ipv4Address(dip);
        boolean isVGWARP = false;
        Short subnetId = null;

        if (vGMAC.equals(Constants.INVALID_MAC_ADDRESS)) {
            LOG.error("Virtual Gateway MAC address is not configured");
        }

        // Locate whether this arp request is for
        Iterator<Entry<Short, AiManagedSubnet>> it = this.subnetMap.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Short, AiManagedSubnet> entry = it.next();
            AiManagedSubnet subnet = entry.getValue();
            if ((subnet.getVirtualGateway() != null) && (subnet.getVirtualGateway().getVirtualGatewayIp() != null)
                    && (subnet.getVirtualGateway().getVirtualGatewayIp().equals(dIPv4))) {
                isVGWARP = true;
                subnetId = entry.getKey();
                break;
            }
        }

        if (false == isVGWARP) {
            return false;
        }

        if (pktIn.pkt.getOperation() != KnownOperation.Request) {
            /* Note: WZJ, if arp replay, should handle by us */
            if (pktIn.pkt.getOperation() == KnownOperation.Reply) {
                notifyHostTracerToAddHost(subnetId, pktIn);
            }

            return true; // It should be handled by us, but it's not request.
        }

        TransmitPacketInput arpReply;

        try {
            // Construct ARP Reply for virtual GW.
            // Virtual GW IP will be used as SPA, Virtual GW MAC will be ethernet source and SHA.
            Ip4Network spa = new Ip4Network(dIPv4.getValue());
            Ip4Network tpa = new Ip4Network(pktIn.pkt.getSourceProtocolAddress());
            // No VLAN in ai deployment.
            Ethernet ether = new ArpPacketBuilder()
                    .setAsReply()
                    .setSenderProtocolAddress(spa)
                    .build(new EtherAddress(vGMAC.getValue()),
                            new EtherAddress(pktIn.pkt.getSourceHardwareAddress()),
                            tpa);

            InstanceIdentifier<Node> node = pktIn.rawPkt.getIngress().getValue().firstIdentifierOf(Node.class);

            arpReply = new TransmitPacketInputBuilder()
                    .setPayload(ether.serialize())
                    .setNode(new NodeRef(node))
                    .setEgress(pktIn.rawPkt.getIngress())
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to build arp reply for vgw " + dIPv4.getValue() +
                    "with request from " + pktIn.pkt.getSourceProtocolAddress());
            return true;
        }

        packetProcessingService.transmitPacket(arpReply);

        return true;
    }

    private void processArp(ArpPacketIn pktIn) {
        boolean vgwHandled = false;
        // Handle arp request for vmac
        vgwHandled = processArpReqForVGW(pktIn);
        if (vgwHandled) {
            return;
        }

        return;
    }

    /**
     * Note: WZJ, process arp probe
     */
    private void processArpProbe() {
        LOG.info("TO BE IMPLEMENTED: ARP PROBE");

        for (Short key : this.subnetTracer.keySet()) {
            sendHostsArpProbe(key, this.subnetTracer.get(key));
        }

        return;
    }

    private void processUserFlowOp(UserFlowOp userFlowOp) {
        if (userFlowOp.op == OFutils.FLOW_ADD) {
            UserFlow userFlow = userFlowOp.userFlow;

            this.ofpluginHelper.addFlow(this.dpid, userFlow.getFlowName(), userFlow.getPriority().intValue(),
                    userFlow.getMatch(), userFlow.getInstructions());
        } else if (userFlowOp.op == OFutils.FLOW_DELETE) {
            UserFlow userFlow = userFlowOp.userFlow;

            this.ofpluginHelper.deleteFlow(this.dpid, userFlow.getFlowName(), userFlow.getPriority().intValue(),
                    userFlow.getMatch());
        }
    }

    /**
     * Note: WZJ, handle add/delete host ip address
     */
    private void handleHostUpdate(Short subnetId, AiManagedSubnet subnetInfo, boolean isDel) {
        if (subnetId == null || subnetInfo == null) {
            LOG.error("HANDLE HOST ADD/DELETE ERROR");
            return;
        }

        /* When host ip added, it should send arp probe */
        List<String> addHostsIp = new ArrayList<>();

        if (!this.subnetTracer.containsKey(subnetId)) {
            /* Handle add new subnet */
            List<String> listIp = new ArrayList<>();
            subnetTracer.put(subnetId, listIp);

            /* Get all of hosts in this subnet */
            List<SubnetHost> listHosts = subnetInfo.getSubnetHost();
            if (listHosts == null || listHosts.isEmpty()) {
                return;
            }

            /* Add all of hosts */
            Iterator itHost = listHosts.iterator();
            while(itHost.hasNext()) {
                SubnetHost hostInfo = (SubnetHost)itHost.next();
                Ipv4Address hostIp = hostInfo.getManagedHostIp();
                listIp.add(hostIp.getValue());
                addHostsIp.add(hostIp.getValue());
                LOG.info("Subnet_id: " + subnetId + " add host :" + hostIp.getValue());
            }
        }
        else {
            if (isDel) {
                /* Delete host info from this subnet */
                if (this.subnetTracer.containsKey(subnetId)) {
                    List<String> listIp = this.subnetTracer.get(subnetId);
                    /* Get all of hosts in this subnet */
                    List<SubnetHost> listHosts = subnetInfo.getSubnetHost();
                    if (listHosts == null || listHosts.isEmpty()) {
                        return;
                    }

                    /* Delete all of hosts */
                    Iterator itHost = listHosts.iterator();
                    while(itHost.hasNext()) {
                        SubnetHost hostInfo = (SubnetHost)itHost.next();
                        Ipv4Address hostIp = hostInfo.getManagedHostIp();
                        if (listIp.contains(hostIp.getValue())) {
                            listIp.remove(hostIp.getValue());
                            notifyHostTracerToDelHost(hostIp.getValue());
                            LOG.info("Subnet_id: " + subnetId + " delete host :" + hostIp.getValue());
                        }
                    }
                }
            }
            else {
                /* Add host info from this subnet */
                if (this.hostTracer.containsKey(subnetId)) {
                    List<String> listIp = this.subnetTracer.get(subnetId);
                    /* Get all of hosts in this subnet */
                    List<SubnetHost> listHosts = subnetInfo.getSubnetHost();
                    if (listHosts == null || listHosts.isEmpty()) {
                        return;
                    }

                    /* Add all of hosts */
                    Iterator itHost = listHosts.iterator();
                    while(itHost.hasNext()) {
                        SubnetHost hostInfo = (SubnetHost)itHost.next();
                        Ipv4Address hostIp = hostInfo.getManagedHostIp();
                        listIp.add(hostIp.getValue());
                        addHostsIp.add(hostIp.getValue());
                        LOG.info("Subnet_id: " + subnetId + " add host :" + hostIp.getValue());
                    }
                }
            }
        }

        if (!addHostsIp.isEmpty()) {
            sendHostsArpProbe(subnetId, addHostsIp);
        }

        return;
    }

    /**
     * Note: WZJ, handle add host info
     */
    private void notifyHostTracerToAddHost(Short subnetId, ArpPacketIn pktIn) {
        if (pktIn == null) {
            return;
        }

        if (this.subnetTracer.containsKey(subnetId)) {
            List<String> listHosts = this.subnetTracer.get(subnetId);
            if (listHosts.contains(pktIn.pkt.getSourceProtocolAddress())) {
                HostInfo hostInfo = new HostInfo(pktIn.pkt.getSourceHardwareAddress(), pktIn.rawPkt.getIngress());
                if (this.hostTracer.containsKey(pktIn.pkt.getSourceProtocolAddress())) {
                    this.hostTracer.remove(pktIn.pkt.getSourceProtocolAddress());
                }
                this.hostTracer.put(pktIn.pkt.getSourceProtocolAddress(), hostInfo);
                LOG.info("host: " + pktIn.pkt.getSourceProtocolAddress() + " add host information");
                LOG.info("packet: " + pktIn.toString());
                addL2ForwardFlows(new MacAddress(pktIn.pkt.getSourceHardwareAddress()), pktIn.rawPkt.getIngress());
            }
        }

        return;
    }

    /**
     * Note: WZJ, handle delete host info
     */
    private void notifyHostTracerToDelHost(String ipAddress) {
        if (ipAddress == null) {
            return;
        }

        if (this.hostTracer.containsKey(ipAddress)) {
            this.hostTracer.remove(ipAddress);
            LOG.info("host: " + ipAddress + " delete host information");
        }
    }

    /**
     * Note: WZJ, Get host mac and ingress port by ip address
     */
    private HostInfo getHostInfoByIpAddr(Ipv4Address ipAddress) {
        if (ipAddress == null) {
            return null;
        }

        if (this.hostTracer.containsKey(ipAddress.getValue())) {
            return this.hostTracer.get(ipAddress.getValue());
        }

        return null;
    }

    /**
     * Note: WZJ, handle hosts arp probe
     */
    private void sendHostsArpProbe(Short subnetId, List<String> listIp) {
        if (subnetId == null || listIp == null) {
            return;
        }

        if (listIp.isEmpty()) {
            return;
        }

        if (!this.subnetMap.containsKey(subnetId)) {
            LOG.info("Subnet_id: " + subnetId + " has not configure virtual gateway, no need handle.");
            return;
        }

        Ipv4Address vIP = this.subnetMap.get(subnetId).getVirtualGateway().getVirtualGatewayIp();
        MacAddress vMAC = this.vGMAC;

        LOG.info("Subnet_id: " + subnetId + " VIP :" + vIP.getValue());
        LOG.info("Subnet_id: " + subnetId + " VMAC :" + vMAC.getValue());

        for(int i = 0; i < listIp.size(); i++)
        {
            String hostIp = listIp.get(i);
            /* Construct arp request packet */
            //TransmitPacketInput arpRequest;
            LOG.info("host_id: " + hostIp + " send arp request");


            /*try {
                /* Subnet VIP will be used as source SPA, VMAC will be used as ethernet source and SHA.
                Ip4Network spa = new Ip4Network(vIP.getValue());
                Ip4Network tpa = new Ip4Network(hostIp);
                // No VLAN in ai deployment.
                Ethernet ether = new ArpPacketBuilder()
                        .setAsRequest()
                        .setSenderProtocolAddress(spa)
                        .build(new EtherAddress(vMAC.getValue()),
                               EtherAddress.BROADCAST,
                               tpa);



                NodeKey nodeKey = new NodeKey(new NodeId(OFutils.BuildNodeIdUriByDpid(this.dpid)));
                InstanceIdentifier<Node> node = InstanceIdentifier.builder(Nodes.class)
                                                .child(Node.class, nodeKey).build();

                arpRequest = new TransmitPacketInputBuilder()
                             .setPayload(ether.serialize())
                             .setNode(new NodeRef(node))
                             .setEgress(OutputPortValues.ALL)
                             .build();
            } catch (Exception e) {
                LOG.error("Failed to build arp request for host: " + hostIp);
                return;
            }*/

            //packetProcessingService.transmitPacket(arpRequest);
        }

        return;
    }

    // TODO: pending zhijun's mac spoofing impl
    // the flow should be:
    // match: ethertype(ipv4)+dst_mac(the virtual gw mac, if the mac is not present, should not install the flow)
    // action: mod_eth_src(quagga interface mac),mod_eth_dst(edge router interface mac),output to uplink interface \
    // (edge router interface ip port learned by mac snooping)
    // in the current topology, the uplink can only be the link between the SDN switch and the L2 switch.
    // So the input of this function is:
    // 1. src_mac (quagga interface mac)
    // 2. dst_mac (edge router interface mac)
    // 3. output_of_port (port where edge router interface mac learned)
    // 4. vmac of this sdn switch.
    private void addDftRouteFlow(MacAddress vGMAC, MacAddress srcMAC, MacAddress dstMAC, NodeConnectorRef output) {
        // Match: IPV4+VGMAC.
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetType(new EthernetTypeBuilder()
                        .setType(new EtherType(Long.valueOf(KnownEtherType.Ipv4.getIntValue()))).build())
                .setEthernetDestination(new EthernetDestinationBuilder().setAddress(vGMAC).build());
        MatchBuilder matchBuilder = new MatchBuilder().setEthernetMatch(ethernetMatchBuilder.build());


        // Actions:mod_dl_src,mod_dl_dst,output
        // NOTE: we only support OPENFLOW 1.3, so we use set_field action.
        // and we should not use dec_ip_ttl because most of hw switch does not support it.

        /* We do not need to create setfield aciotion explicitly, flowconverter in openflowplugin
           will do that for me, leave these code for future reference.

           See flowconverter class in openflowplugin.
        List<MatchEntry> setFieldMatchEntries = new ArrayList<MatchEntry>();
        MatchEntryBuilder srcMatchEntryBuilder = new MatchEntryBuilder();
        srcMatchEntryBuilder.setOxmClass(OpenflowBasicClass.class)
                            .setOxmMatchField(EthSrc.class)
                            .setHasMask(false)
                            .setMatchEntryValue(new EthSrcCaseBuilder()
                                    .setEthSrc(new EthSrcBuilder()
                                            .setMacAddress(srcMAC)
                                            .build())
                                    .build());
        MatchEntryBuilder dstMatchEntryBuilder = new MatchEntryBuilder();
        dstMatchEntryBuilder.setOxmClass(OpenflowBasicClass.class)
                .setOxmMatchField(EthDst.class)
                .setHasMask(false)
                .setMatchEntryValue(new EthDstCaseBuilder()
                        .setEthDst(new EthDstBuilder()
                                .setMacAddress(dstMAC)
                                .build())
                        .build());
        setFieldMatchEntries.add(srcMatchEntryBuilder.build());
        setFieldMatchEntries.add(dstMatchEntryBuilder.build());
        */

        ActionBuilder modDlSrcActionBuilder = new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new SetDlSrcActionCaseBuilder()
                        .setSetDlSrcAction(new SetDlSrcActionBuilder()
                                .setAddress(srcMAC)
                                .build())
                        .build());
        ActionBuilder modDlDstActionBuilder = new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new SetDlDstActionCaseBuilder()
                        .setSetDlDstAction(new SetDlDstActionBuilder()
                                .setAddress(dstMAC)
                                .build())
                        .build());
        Uri destPortUri = output.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
        ActionBuilder outputActionBuilder = new ActionBuilder()
                .setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                                .setMaxLength(0xffff)
                                .setOutputNodeConnector(destPortUri)
                                .build())
                        .build());

        List<Action> actions = new ArrayList<Action>();
        actions.add(modDlSrcActionBuilder.build());
        actions.add(modDlDstActionBuilder.build());
        actions.add(outputActionBuilder.build());
        ApplyActions applyActions = new ApplyActionsBuilder().setAction(actions).build();
        InstructionBuilder applyActionsInstructionBuilder = new InstructionBuilder()
                .setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder()
                        .setApplyActions(applyActions)
                        .build());
        InstructionsBuilder instructionsBuilder = new InstructionsBuilder() //
                .setInstruction(ImmutableList.of(applyActionsInstructionBuilder.build()));

        this.ofpluginHelper.addFlow(this.dpid, Constants.XOS_APP_DFT_ROUTE_FLOW_NAME,
                Constants.XOS_APP_DFT_ROUTE_FLOW_PRIORITY,
                matchBuilder.build(), instructionsBuilder.build());

        // Note: we need install default arp flow for both active and backup switch.

        // Action 2: store to our md sal datastore.

        this.mdsalHelper.storeAppFlow(this.nodeId, Constants.XOS_APP_DFT_ROUTE_FLOW_NAME,
                matchBuilder.build(), instructionsBuilder.build());

        LOG.info("Pushed init flow {} to the switch {}", Constants.XOS_APP_DFT_ROUTE_FLOW_NAME, this.dpid);
    }

    private void tryAddDftRouteFlow() {
        // TODO: check condition that required for addDftRouteFlow is satisfied, then
        // call it to add dft route flow.

        // TEST CODE:

        InstanceIdentifier<NodeConnector> iid = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(OFutils.BuildNodeIdUriByDpid(dpid))))
                .child(NodeConnector.class, new NodeConnectorKey(new NodeConnectorId("openflow:1")))
                .build();
        addDftRouteFlow(this.vGMAC, new MacAddress("00:00:00:00:00:02"),
                new MacAddress("00:00:00:00:00:01"),
                new NodeConnectorRef(iid));
    }

    private void processVirtualGatewayMacUpdate(VirtualGatewayMacUpdate update) {
        // Only handle changes.
        if (!update.address.equals(this.vGMAC)) {
            this.vGMAC = update.address;
            tryAddDftRouteFlow();
        }
    }

    private void processEdgeRouterInterfaceIpUpdate(EdgeRouterInterfaceIpUpdate update) {
        // Only handle changes.
        if (!update.address.equals(this.edgeRouterInterfaceIp)) {
            // TODO: use zhijun's local db to obtain the interface port and mac, then construct a flow for
            // default rule.
            tryAddDftRouteFlow();
        }
    }

    private void processQuaggaInterfaceIpUpdate(QuaggaInterfaceIpUpdate update) {
        // Only handle changes.
        if (!update.address.equals(this.quaggaInterfaceIp)) {
            // TODO: use zhijun's local db to obtain the interface port and mac, then construct a flow for
            // default rule.
            tryAddDftRouteFlow();
        }
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof DpIdCreated) {
            processDpid(((DpIdCreated) (message)).getDpId());
        } else if (message instanceof SwitchConnected) {
            processSwitchConnected();
        } else if (message instanceof SwitchDisconnected) {
            processSwitchDisonnected();
        } else if (message instanceof AppStatusUpdate) {
            processAppStatusUpdate(((AppStatusUpdate) (message)).appStatus);
        } else if (message instanceof ProbeArpOnce) {
            processArpProbe();
        } else if (message instanceof ManagedSubnetUpdate) {
            processSubnetUpdate((ManagedSubnetUpdate) message);
        } else if (message instanceof ArpPacketIn) {
            processArp((ArpPacketIn) message);
        } else if (message instanceof UserFlowOp) {
            processUserFlowOp((UserFlowOp) message);
        } else if (message instanceof VirtualGatewayMacUpdate) {
            processVirtualGatewayMacUpdate((VirtualGatewayMacUpdate) message);
        } else if (message instanceof EdgeRouterInterfaceIpUpdate) {
            processEdgeRouterInterfaceIpUpdate((EdgeRouterInterfaceIpUpdate) message);
        } else if (message instanceof QuaggaInterfaceIpUpdate) {
            processQuaggaInterfaceIpUpdate((QuaggaInterfaceIpUpdate) message);
        } else {
            unhandled(message);
        }
    }

    public static Props props(final PacketProcessingService packetProcessingService,
                              final SalFlowService salFlowService,
                              final DataBroker dataService) {
        return Props.create(new SdnSwitchActorCreator(packetProcessingService, salFlowService, dataService));
    }

    private static final class SdnSwitchActorCreator implements Creator<SdnSwitchActor> {
        private final PacketProcessingService packetProcessingService;
        private final SalFlowService salFlowService;
        private final DataBroker dataService;

        SdnSwitchActorCreator(final PacketProcessingService packetProcessingService,
                              final SalFlowService salFlowService,
                              final DataBroker dataService) {
            this.packetProcessingService = Preconditions.checkNotNull(packetProcessingService);
            this.salFlowService = Preconditions.checkNotNull(salFlowService);
            this.dataService = Preconditions.checkNotNull(dataService);
        }

        @Override
        public SdnSwitchActor create() {
            return new SdnSwitchActor(packetProcessingService, salFlowService, dataService);
        }
    }
}
