package ac.at.tuwien.infosys.visp.runtime.topology.rabbitMq;

import ac.at.tuwien.infosys.visp.common.operators.Operator;
import ac.at.tuwien.infosys.visp.runtime.datasources.DockerContainerRepository;
import ac.at.tuwien.infosys.visp.runtime.datasources.DockerHostRepository;
import ac.at.tuwien.infosys.visp.runtime.datasources.entities.DockerContainer;
import ac.at.tuwien.infosys.visp.runtime.resourceManagement.DockerContainerManagement;
import ac.at.tuwien.infosys.visp.runtime.resourceManagement.ManualOperatorManagement;
import ac.at.tuwien.infosys.visp.runtime.topology.TopologyUpdate;
import ac.at.tuwien.infosys.visp.runtime.topology.operatorUpdates.SourcesUpdate;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.spotify.docker.client.exceptions.DockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;


@Service
public class RabbitMqManager {
    /**
     * This class is used to interact with a specific rabbitmq host
     */

    @Autowired
    private DockerContainerManagement dcm;

    @Autowired
    private DockerHostRepository dhr;

    @Autowired
    private DockerContainerRepository dcr;

    @Autowired
    private ManualOperatorManagement rpp;

    @Value("${spring.rabbitmq.username}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitmqPassword;

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMqManager.class);

    public static String getQueueName(String senderHost, String senderOperator, String consumerOperator) {
        // returns the name of the queue that is created for the communication between senderHost and consumerHost
        //return consumerHost + senderHost;
        if (senderOperator.contains("/") || consumerOperator.contains("/") || senderHost.contains("/")) {
            throw new RuntimeException("Neither senderOperator, consumerOperator nor senderHost may contain slashes");
        }
        if (senderOperator.contains(">") || consumerOperator.contains(">") || senderHost.contains(">")) {
            throw new RuntimeException("Neither senderOperator, consumerOperator nor senderHost may contain greater signs");
        }
        return senderHost + "/" + senderOperator + ">" + consumerOperator;

    }

    public Channel createChannel(String infrastructureHost) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(infrastructureHost);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        Connection connection;
        connection = factory.newConnection();
        Channel channel = connection.createChannel();
        return channel;
    }

    public void addMessageFlow(String fromOperatorId, String toOperatorId,
                               String fromInfrastructureHost, String toInfrastructureHost)
            throws IOException, TimeoutException {
        /**
         * add a message flow from operator FROM to operator TO
         */
        ChannelFactory channelFactory = new ChannelFactory(fromInfrastructureHost, toInfrastructureHost).invoke();
        Channel toChannel = channelFactory.getToChannel();
        Channel fromChannel = channelFactory.getFromChannel();

        try {

            String exchangeName = fromOperatorId;
            toChannel.exchangeDeclare(exchangeName, "fanout", true);

            String queueName = getQueueName(fromInfrastructureHost, fromOperatorId, toOperatorId);
            fromChannel.queueDeclare(queueName, true, false, false, null);

            // tell exchange to send msgs to queue:
            fromChannel.queueBind(queueName, exchangeName, exchangeName); // third parameter is ignored in fanout mode

            sendDockerSignalForUpdate(toOperatorId, "ADD " + fromInfrastructureHost + "/" + fromOperatorId + ">" + toOperatorId);

        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage());
        } catch (DockerException e) {
            LOG.error(e.getLocalizedMessage());
        } finally {
            toChannel.close();
            if (!toInfrastructureHost.equals(fromInfrastructureHost)) {
                fromChannel.close();
            }
            // TODO: close connection
        }
    }

    private void sendDockerSignalForUpdate(String toOperatorId, String updateCommand) throws DockerException, InterruptedException {
        List<DockerContainer> dcs = dcr.findByOperator(toOperatorId);
        for (DockerContainer dc : dcs) {
            String command = "echo \"" + updateCommand + "\" >> ~/topologyUpdate";
            dcm.executeCommand(dc, command);
            LOG.info("Executing command on dockercontainer " + dc.getContainerid() + ": [" + command + "]");
        }
    }

    public void removeMessageFlow(String fromOperatorId, String toOperatorId,
                                  String fromInfrastructureHost, String toInfrastructureHost) throws IOException, TimeoutException {
        ChannelFactory channelFactory = new ChannelFactory(fromInfrastructureHost, toInfrastructureHost).invoke();
        Channel toChannel = channelFactory.getToChannel();
        Channel fromChannel = channelFactory.getFromChannel();

        try {

            String exchangeName = fromOperatorId;
            //toChannel.exchangeDeclare(exchangeName, "fanout", true);

            String queueName = getQueueName(fromInfrastructureHost, fromOperatorId, toOperatorId);
            //fromChannel.queueDeclare(queueName, true, false, false, null);

            //fromChannel.queueBind(queueName, exchangeName, ""); // third parameter is ignored in fanout mode

            // TODO: check whether we also have to unbind queue and exchange (probably not bc there could be other
            //   queues that also depend on the same exchange?

            // this should stop the exchange sending messages to the queue
            fromChannel.queueUnbind(queueName, exchangeName, exchangeName);
            LOG.info("!unbinding queue " + queueName + " on exchange " + exchangeName + " on host " + fromInfrastructureHost);

            sendDockerSignalForUpdate(toOperatorId, "REMOVE " + fromInfrastructureHost + "/" + fromOperatorId + ">" + toOperatorId);

        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage());
        } catch (DockerException e) {
            LOG.error(e.getLocalizedMessage());
        } finally {
            toChannel.close();
            if (!toInfrastructureHost.equals(fromInfrastructureHost)) {
                fromChannel.close();
            }
            // TODO: close connection
        }
    }

    public void performUpdates(List<TopologyUpdate> updates) {
        for (TopologyUpdate update : updates) {
            LOG.info("Performing update: " + update.toString());
            switch (update.getAction()) {
                case UPDATE_OPERATOR:
                    handleUpdateOperator(update);
                    break;
                case ADD_OPERATOR:
                    handleAddOperator(update);
                    break;
                case REMOVE_OPERATOR:
                    handleRemoveOperator(update);
                    break;
                default:
                    LOG.error("Unknown action: " + update.getAction() + " for update " + update.toString());
                    break;
            }
        }
        LOG.info("All updates were applied");
    }

    private void handleRemoveOperator(TopologyUpdate update) {
        LOG.info("Handling remove operator update");
        for (Operator source : update.getAffectedOperator().getSources()) {
            LOG.info("Removing message flow between operators " + source.getName() + " and " + update.getAffectedOperator().getName());
            try {
                removeMessageFlow(source.getName(), update.getAffectedOperator().getName(), source.getConcreteLocation().getIpAddress(),
                        update.getAffectedOperator().getConcreteLocation().getIpAddress());
            } catch (IOException | TimeoutException e) {
                LOG.error(e.getLocalizedMessage());
            }
        }
    }

    private void handleAddOperator(TopologyUpdate update) {
        LOG.info("Handling add operator update");
        rpp.addOperator(update.getAffectedOperator());
        for(Operator source : update.getAffectedOperator().getSources()) {
            // for each source, add message flow to this operator
            // TODO: check if the sources already exist!
            try {
                LOG.info("Adding message flow between operators " + source.getName() + " and " + update.getAffectedOperatorId());
                addMessageFlow(source.getName(), update.getAffectedOperatorId(), source.getConcreteLocation().getIpAddress(),
                        update.getAffectedOperator().getConcreteLocation().getIpAddress());
            } catch (Exception e) {
                LOG.error(e.getLocalizedMessage());
            }
        }
    }

    private void handleUpdateOperator(TopologyUpdate update) {
        LOG.info("Handling update operator update");
        switch (update.getUpdateType()) {
            case UPDATE_SOURCE:
                handleSourceUpdate(update);
                break;
            default:
                throw new RuntimeException("Update type " + update.getUpdateType().toString() + " not yet implemented");
                // TODO: implement others
        }
    }

    private void handleSourceUpdate(TopologyUpdate update) {
        SourcesUpdate sourcesUpdate = (SourcesUpdate) update.getChangeToBeExecuted();
        // add message flow for new sources:
        List<Operator> newSources = sourcesUpdate.getNewSources();
        List<Operator> oldSources = sourcesUpdate.getOldSources();
        for (Operator sourceEntry : newSources) {
            if (!oldSources.contains(sourceEntry)) {
                // add new flow from the new source to our target operator
                try {
                    LOG.info("Adding message flow between operators " + sourceEntry.getName() + " and " + update.getAffectedOperatorId());
                    addMessageFlow(sourceEntry.getName(), update.getAffectedOperatorId(), sourceEntry.getConcreteLocation().getIpAddress(),
                            update.getAffectedOperator().getConcreteLocation().getIpAddress());
                } catch (IOException | TimeoutException e) {
                    LOG.error(e.getLocalizedMessage());
                }
            }
        }

        for (Operator sourceEntry : oldSources) {
            if (!newSources.contains(sourceEntry)) {
                // remove message flow from old source
                try {
                    LOG.info("Removing message flow between operators " + sourceEntry.getName() + " and " + update.getAffectedOperatorId());
                    removeMessageFlow(sourceEntry.getName(), update.getAffectedOperatorId(), sourceEntry.getConcreteLocation().getIpAddress(),
                            update.getAffectedOperator().getConcreteLocation().getIpAddress());
                } catch (IOException | TimeoutException e) {
                    LOG.error(e.getLocalizedMessage());
                }
            }
        }

        // remove message flow for old sources
    }

    private class ChannelFactory {
        private String fromInfrastructureHost;
        private String toInfrastructureHost;
        private Channel fromChannel;
        private Channel toChannel;

        public ChannelFactory(String fromInfrastructureHost, String toInfrastructureHost) {
            this.fromInfrastructureHost = fromInfrastructureHost;
            this.toInfrastructureHost = toInfrastructureHost;
        }

        public Channel getFromChannel() {
            return fromChannel;
        }

        public Channel getToChannel() {
            return toChannel;
        }

        public ChannelFactory invoke() throws IOException, TimeoutException {
            fromChannel = createChannel(fromInfrastructureHost);
            toChannel = null;
            if (!fromInfrastructureHost.equals(toInfrastructureHost)) {
                // the two nodes are not on the same infrastructure host
                toChannel = createChannel(toInfrastructureHost);
            } else {
                toChannel = fromChannel;
            }
            return this;
        }
    }
}
