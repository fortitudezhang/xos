package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.main.rev150820;

import com.xsdn.main.config.ConfigDataListener;
import com.xsdn.main.packet.PacketInHandler;
import com.xsdn.main.rpc.XosRpcProvider;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.SalGroupService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.main.rev150820.modules.module.configuration.xos.BindingAwareBroker;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XosModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.main.rev150820.AbstractXosModule {
    private final static Logger LOG = LoggerFactory.getLogger(XosModule.class);
    // Thread poll which will be used to process pkt in message.
    private final ExecutorService pktInExecutor = Executors.newCachedThreadPool();
    private PacketInHandler packetInHandler;
    private Registration packetInListener = null;
    private ConfigDataListener configDataListener = null;

    public XosModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public XosModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.xos.main.rev150820.XosModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        LOG.info("createInstance invoked for the xos module.");
        NotificationProviderService notificationService = getNotificationServiceDependency();
        DataBroker dataService = getDataBrokerDependency();
        RpcProviderRegistry rpcRegistryDependency = getRpcRegistryDependency();
        SalFlowService salFlowService = rpcRegistryDependency.getRpcService(SalFlowService.class);
        SalGroupService salGroupService = rpcRegistryDependency.getRpcService (SalGroupService.class);
        PacketProcessingService packetProcessingService =
                rpcRegistryDependency.getRpcService(PacketProcessingService.class);

        // Register xos rpc.
        getBindingAwareBrokerDependency().registerProvider(new XosRpcProvider());

        // TODO: install a to controller flow.

        // Start the pkt in processing module.
        // TODO: need consider the clustering case, in that case, we may ensure there are only
        // the thread can be used process pkt in backgroud, now for simple, just process in the pkt callback.
        //pktInExecutor.submit();
        packetInHandler = new PacketInHandler();
        packetInListener = notificationService.registerNotificationListener(packetInHandler);

        // Register config handler.
        configDataListener = new ConfigDataListener(dataService);

        final class CloseXosResource implements AutoCloseable {
            @Override
            public void close() throws Exception {

                // pktInExecutor.shutdown();

                if (packetInListener != null) {
                    packetInListener.close();
                }

                return;
            }
        }

        AutoCloseable ret = new CloseXosResource();
        LOG.info("XOS(instance {}) initialized.", ret);
        return ret;
    }

}
