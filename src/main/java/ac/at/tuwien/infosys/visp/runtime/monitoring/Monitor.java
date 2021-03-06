package ac.at.tuwien.infosys.visp.runtime.monitoring;

import ac.at.tuwien.infosys.visp.common.operators.Operator;
import ac.at.tuwien.infosys.visp.common.operators.ProcessingOperator;
import ac.at.tuwien.infosys.visp.common.operators.Source;
import ac.at.tuwien.infosys.visp.runtime.configuration.Configurationprovider;
import ac.at.tuwien.infosys.visp.runtime.datasources.ProcessingDurationRepository;
import ac.at.tuwien.infosys.visp.runtime.datasources.QueueMonitorRepository;
import ac.at.tuwien.infosys.visp.runtime.datasources.entities.ProcessingDuration;
import ac.at.tuwien.infosys.visp.runtime.datasources.entities.QueueMonitor;
import ac.at.tuwien.infosys.visp.runtime.entities.ScalingAction;
import ac.at.tuwien.infosys.visp.runtime.resourceManagement.connectors.impl.OpenstackConnector;
import ac.at.tuwien.infosys.visp.runtime.topology.TopologyManagement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
@DependsOn("configurationprovider")
public class Monitor {

    private static final Logger LOG = LoggerFactory.getLogger(OpenstackConnector.class);

    @Value("${spring.rabbitmq.username}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitmqPassword;

    @Value("${visp.relaxationfactor}")
    private Double relaxationfactor;

    @Value("${visp.minimalqueueloadforupscaling}")
    private Integer queueUpscalingThreshold;

    @Autowired
    private ProcessingDurationRepository pcr;

    @Autowired
    private QueueMonitorRepository qmr;

    @Autowired
    private TopologyManagement topologyMgmt;

    @Autowired
    private Configurationprovider config;

    @Scheduled(fixedRateString = "${visp.monitor.period}")
    public void recordData() {
        List<Operator> runningOperators = topologyMgmt.getOperatorsForAConcreteLocation(config.getRuntimeIP());

        for (Operator op : runningOperators) {
            List<String> queues = topologyMgmt.getIncomingQueuesAsList(op.getName());

            for (String queue : queues) {
                QueueMonitor qm = getQueueCount(queue, op);

                if (qm == null) {
                    continue;
                }

                qmr.save(qm);
            }
        }
    }





    public ScalingAction analyze(Operator operator) {
        List<String> queues = topologyMgmt.getIncomingQueuesAsList(operator.getName());

        Integer max = 0;
        Integer min = 0;

        for (String queue : queues) {
            QueueMonitor qm = getQueueCount(queue, operator);

            if (qm == null) {
                continue;
            }

            Integer queueCount = qm.getAmount();
            if (queueCount < min) {
                min = queueCount;
            }

            if (queueCount > max) {
                max = queueCount;
            }
        }

        ScalingAction sa = upscalingDuration(operator, max);

        if (sa == ScalingAction.DONOTHING) {
            //assume that we may not have any monitoring information
            if (max>queueUpscalingThreshold*5) {
                return ScalingAction.SCALEUP;
            }
        }
        return sa;
    }


    public ScalingAction analyzeBasic(Operator operator, Integer upscaling, Integer downscaling) {
        List<String> queues = topologyMgmt.getIncomingQueuesAsList(operator.getName());

        Integer max = 0;
        Integer min = 0;

        for (String queue : queues) {
            QueueMonitor qm = getQueueCount(queue, operator);

            if (qm == null) {
                continue;
            }

            Integer queueCount = qm.getAmount();
            if (queueCount < min) {
                min = queueCount;
            }

            if (queueCount > max) {
                max = queueCount;
            }
        }

        if (max > (upscaling * 5)) {
            return ScalingAction.SCALEUPDOUBLE;
        }

        if (max > upscaling) {
            return ScalingAction.SCALEUP;
        }

        if (max < downscaling) {
            return ScalingAction.SCALEDOWN;
        }

        return ScalingAction.DONOTHING;
    }


    public void saveQueueCount(String operator, String infrastructureHost) {

    	List<String> queues = topologyMgmt.getIncomingQueuesAsList(operator);

    	//TODO fixme
        for (String queue : queues) {
        	qmr.save(getQueueCount(queue, null));
        }
    }

    private ScalingAction upscalingDuration(Operator operator, Integer maxQueue) {
        List<ProcessingDuration> pds = pcr.findFirst5ByOperatorOrderByIdDesc(operator.getName());

        if (pds.isEmpty()) {
            if (operator instanceof Source) {
                return ScalingAction.DONOTHING;
            }
            return ScalingAction.DONOTHING;
        }

        if (pds.get(0).getDuration() * relaxationfactor > ((ProcessingOperator) topologyMgmt.getOperatorByIdentifier(operator.getName())).getExpectedDuration()) {
            if (maxQueue > queueUpscalingThreshold) {
                return ScalingAction.SCALEUP;
            }

        }

        Integer count = 4;
        double[][] data = new double[5][2];
        for (ProcessingDuration pd : pds) {
            data[count][0] = count;
            data[count][1] = pd.getDuration();
            count--;
        }

        SimpleRegression regression = new SimpleRegression(false);
        regression.addData(data);

        Double expectedDurationValue = regression.predict(6);

        if (expectedDurationValue * relaxationfactor > ((ProcessingOperator) topologyMgmt.getOperatorByIdentifier(operator.getName())).getExpectedDuration()) {
            if (maxQueue > queueUpscalingThreshold) {
                return ScalingAction.SCALEUP;
            }
        }

        return ScalingAction.DONOTHING;
    }


    private ScalingAction upscalingQueue(String operator, Integer max) {
        if (max > ((ProcessingOperator) topologyMgmt.getOperatorByIdentifier(operator)).getQueueThreshold()) {
            return ScalingAction.SCALEUP;
        }

        List<QueueMonitor> qms = qmr.findFirst5ByOperatorOrderByIdDesc(operator);

        Integer count = 4;
        double[][] data = new double[5][2];
        for (QueueMonitor qm : qms) {
            data[count][0] = count;
            data[count][1] = qm.getAmount();
            count--;
        }

        SimpleRegression regression = new SimpleRegression(false);
        regression.addData(data);

        Double expectedDurationValue = regression.predict(6);

        if (expectedDurationValue > ((ProcessingOperator) topologyMgmt.getOperatorByIdentifier(operator)).getQueueThreshold()) {
            return ScalingAction.SCALEUP;
        }

        return ScalingAction.DONOTHING;
    }

    
    
    private QueueMonitor getQueueCount(String queueName, Operator op) {
        String operatorName = op.getName();
        String infrastructureHost = op.getConcreteLocation().getIpAddress();

        RestTemplate restTemplate = new RestTemplateBuilder()
                .basicAuthorization(rabbitmqUsername, rabbitmqPassword)
                .build();

        String queueNameRaw = queueName;
        queueName = queueName.replace("/", "%2F");
        queueName = queueName.replace(">", "%3E");

        String URI = "http://" + infrastructureHost + ":15672/api/queues/%2F/" + queueName;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(URI);
        UriComponents components = builder.build(true);

        ResponseEntity<BaseJsonNode> response = restTemplate.exchange(components.toUri(), HttpMethod.GET, null, BaseJsonNode.class);

        try {
            BaseJsonNode arrayNode = response.getBody();
            Double incoming = 0.0;
            Integer queueload = 0;
            if (arrayNode!=null) {
                JsonNode rateNode = arrayNode.findValue("message_stats").findValue("publish_details");

                if (rateNode != null) {
                    if (rateNode.findValue("rate")!=null) {
                        incoming = rateNode.findValue("rate").asDouble();
                    }
                }

            queueload = arrayNode.findValue("messages").asInt();
            LOG.debug("Current load for queue: " + queueNameRaw + " is " + queueload + ".");
            }
            return new QueueMonitor(new DateTime(DateTimeZone.UTC), operatorName, queueNameRaw, queueload, incoming);
        } catch (Exception ex) {
            LOG.warn("Queue \"" + queueNameRaw + "\" is not available.");
        }
    return null;

    }
}
