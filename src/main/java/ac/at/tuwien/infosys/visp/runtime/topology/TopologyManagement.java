package ac.at.tuwien.infosys.visp.runtime.topology;


import ac.at.tuwien.infosys.visp.common.operators.Operator;
import ac.at.tuwien.infosys.visp.runtime.topology.rabbitMq.RabbitMqManager;
import ac.at.tuwien.infosys.visp.topologyParser.TopologyParser;
import com.google.common.base.Joiner;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Service
public class TopologyManagement {
    /**
     * this class is used to manage the topology of a specific VISP runtime
     */

    @Value("${spring.rabbitmq.username}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitmqPassword;

    private String dotFile;

    private Map<String, Operator> topology = new LinkedHashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagement.class);

    /**
     * path to the deploymentfile that will be executed after the deployment has been tested
     */
    private File testDeploymentFile;
    private int testDeploymentHash;

    public Map<String, Operator> getTopology() {
        return topology;
    }

    public void setTopology(Map<String, Operator> topology) {
        this.topology = topology;
    }

    public void createMapping(String infrastructureHost) {
        try {

            // TODO: all these exchanges and queues must be created at the appropriate rabbitmq hosts

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(infrastructureHost);
            factory.setUsername(rabbitmqUsername);
            factory.setPassword(rabbitmqPassword);
            Connection connection = null;
            connection = factory.newConnection();
            Channel channel = connection.createChannel();
            

            //declare error message channel
            channel.exchangeDeclare("error", "fanout", true);
            channel.queueDeclare("error", true, false, false, null);
            channel.queueBind("error", "error", "error");

            channel.exchangeDeclare("processingduration", "fanout", true);
            channel.queueDeclare("processingduration", true, false, false, null);
            channel.queueBind("processingduration", "processingduration", "processingduration");
            
            /* Declare Management Message Channels */
            channel.exchangeDeclare("applicationmetrics", "fanout", true);
            channel.queueDeclare("applicationmetrics", true, false, false, null);
            channel.queueBind("applicationmetrics", "applicationmetrics", "applicationmetrics");

            channel.close();
            connection.close();

        } catch (IOException e) {
            LOG.error("Could not cleanup topology.", e);
        } catch (TimeoutException e) {
            LOG.error("Could not cleanup topology.", e);
        }
    }

    public void reset(String infrastructureHost) {
        cleanup(infrastructureHost);
        createMapping(infrastructureHost);
    }

    public void cleanup(String infrastructureHost) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(infrastructureHost);
            factory.setUsername(rabbitmqUsername);
            factory.setPassword(rabbitmqPassword);
            Connection connection = null;
            connection = factory.newConnection();
            Channel channel = connection.createChannel();

            for (Operator n : topology.values()) {
                channel.exchangeDelete(n.getName());

                if (n.getSources() != null) {
                    for (Operator source : n.getSources()) {
                        String queueName = RabbitMqManager.getQueueName(infrastructureHost, source.getName(), n.getName());
                        LOG.info("Deleting queue " + queueName);
                        channel.queueDelete(queueName);
                        // TODO: check why this is not executed
                    }
                }
            }

            channel.close();
            connection.close();

        } catch (IOException e) {
            LOG.error("Could not create mapping.", e);
        } catch (TimeoutException e) {
            LOG.error("Could not create mapping.", e);
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage());
        }
    }

    public String getIncomingQueues(String operator) {
        StringBuilder incomingQueues = new StringBuilder();
        for (Operator op : topology.values()) {
            if (op.getName().equals(operator)) {
                if (op.getSources()!=null) {
                    for (Operator source : op.getSources()) {
                        incomingQueues.append(RabbitMqManager.getQueueName(source.getConcreteLocation().getIpAddress(), source.getName(), op.getName())).append("_");
                    }
                }
            }
        }
        return incomingQueues.toString();
    }

    public List<String> getIncomingQueuesAsList(String operator) {
        List<String> incomingQueues = new ArrayList<>();
        for (Operator op : topology.values()) {
            if (op.getName().equals(operator)) {
                if (op.getSources()!=null) {
                    for (Operator source : op.getSources()) {
                        incomingQueues.add(RabbitMqManager.getQueueName(source.getConcreteLocation().getIpAddress(), source.getName(), op.getName()));
                    }
                }
            }
        }
        return incomingQueues;
    }

    public List<String> getOperatorsAsList() {
        List<String> operators = new ArrayList<>();
        for (Operator op : topology.values()) {
            operators.add(op.getName());
        }
        return operators;
    }

    public List<Operator> getOperators() {
        List<Operator> operators = new ArrayList<>();
        for (Operator op : topology.values()) {
            operators.add(op);
        }
        return operators;
    }

    public List<Operator> getOperatorsForAConcreteLocation(String location) {
        List<Operator> operators = new ArrayList<>();
        for (Operator op : topology.values()) {
            if (op.getConcreteLocation().getIpAddress().equals(location)) {
                operators.add(op);
            }
        }
        return operators;
    }

    public Operator getOperatorByIdentifier(String identifier) {
        return topology.get(identifier);
    }
    
    public String getDownstreamOperators(String operator){
    
    	Set<String> ops = new HashSet<String>();
    	
        for (Operator n : topology.values()) {

        	if (n.getSources() == null)
            	continue;
        	
            for (Operator source : n.getSources()) {
            	if (source.getName().equals(operator)){
            		ops.add(n.getName());
            	}
            }
        }

        return Joiner.on(',').join(ops);
   
    }

    public List<Operator> getDownstreamOperatorsAsList(Operator operator) {
        List<Operator> resultset = new ArrayList<>();

        for(Operator o : topology.values()) {
            if(o.getSources() == null) {
                continue;
            }
            for( Operator source : o.getSources()) {
                if(source.getName().equals(operator.getName())) {
                    resultset.add(o);
                }
            }
        }

        return resultset;
    }

    public String getDotfile() {
        return dotFile;
    }


    public void setDotFile(String dotFile) {
        this.dotFile = dotFile;
    }

    public void saveTestDeploymentFile(File topologyFile, int hashcode) {
        /**
         * this method is called by the handling of a REST-call coming from another VISP instance.
         * It stores the topology-file for later deployment
         */

        this.testDeploymentFile = topologyFile;
        this.testDeploymentHash = hashcode;
    }

    public File getTestDeploymentFile() {
        return testDeploymentFile;
    }

    public int getTestDeploymentHash() {
        return testDeploymentHash;
    }
}
