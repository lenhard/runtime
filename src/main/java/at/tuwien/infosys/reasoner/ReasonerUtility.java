package at.tuwien.infosys.reasoner;

import at.tuwien.infosys.configuration.OperatorConfiguration;
import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.datasources.DockerHostRepository;
import at.tuwien.infosys.datasources.ProcessingDurationRepository;
import at.tuwien.infosys.datasources.ScalingActivityRepository;
import at.tuwien.infosys.entities.DockerContainer;
import at.tuwien.infosys.entities.DockerHost;
import at.tuwien.infosys.entities.ProcessingDuration;
import at.tuwien.infosys.entities.ResourceAvailability;
import at.tuwien.infosys.topology.TopologyManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReasonerUtility {

    @Autowired
    private DockerHostRepository dhr;

    @Autowired
    private DockerContainerRepository dcr;

    @Autowired
    private TopologyManagement tmgmt;

    @Autowired
    private ProcessingDurationRepository pcr;

    @Autowired
    private ScalingActivityRepository scr;

    @Autowired
    private OperatorConfiguration opConf;

    @Value("${visp.relaxationfactor}")
    private Double relaxationfactor;

    @Value("${visp.penaltycosts}")
    private Double penaltycosts;


    private static final Logger LOG = LoggerFactory.getLogger(ReasonerUtility.class);

    public ResourceAvailability calculateFreeresources(List<ResourceAvailability> resources) {
        ResourceAvailability all = new ResourceAvailability();
        all.setAmountOfContainer(0);
        all.setCpuCores(0.0);
        all.setRam(0);
        all.setStorage(0.0F);


        LOG.info("###### free resources ######");
        for (ResourceAvailability ra : resources) {
            all.setAmountOfContainer(all.getAmountOfContainer() + ra.getAmountOfContainer());
            all.setCpuCores(all.getCpuCores() + ra.getCpuCores());
            all.setRam(all.getRam() + ra.getRam());
            all.setStorage(all.getStorage() + ra.getStorage());

            LOG.info(ra.getHost().getName() + " - Container: " + ra.getAmountOfContainer() + " - CPU: " + ra.getCpuCores() + " - RAM: " + ra.getRam() + " - Storage: " + ra.getStorage());
        }
        LOG.info("###### free resources ######");

        return all;
    }

    public List<ResourceAvailability> calculateFreeResourcesforHosts(DockerHost blacklistedHost) {
        Map<String, ResourceAvailability> hostResourceUsage = new HashMap<>();
        List<ResourceAvailability> freeResources = new ArrayList<>();

        for (DockerHost dh : dhr.findAll()) {
            ResourceAvailability rc = new ResourceAvailability(dh, 0, 0.0, 0, 0.0F);
            hostResourceUsage.put(dh.getName(), rc);
        }

        //collect current usage of cloud resources
        for (DockerContainer dc : dcr.findAll()) {
            if (dc.getStatus().equals("stopping")) {
                continue;
            }

            ResourceAvailability rc = hostResourceUsage.get(dc.getHost());
            rc.setAmountOfContainer(rc.getAmountOfContainer() + 1);
            rc.setCpuCores(rc.getCpuCores() + dc.getCpuCores());
            rc.setRam(rc.getRam() + dc.getRam());
            rc.setStorage(rc.getStorage() + dc.getStorage());
            hostResourceUsage.put(dc.getHost(), rc);
        }

        //calculate how much resources are left on a specific host

        for (Map.Entry<String, ResourceAvailability> entry : hostResourceUsage.entrySet()) {
            String name = entry.getKey();
            ResourceAvailability usage = entry.getValue();
            DockerHost dh = dhr.findByName(name).get(0);

            if (blacklistedHost != null) {
                if (dh.getName().equals(blacklistedHost.getName())) {
                    LOG.info("omitted host: " + dh.getName() + "for scheduling, since it is targeted to be shut down.");
                    continue;
                }
            }

            if (dh.getScheduledForShutdown()) {
                LOG.info("omitted host: " + dh.getName() + "for scheduling, since it is scheduled to shut down.");
                continue;
            }

            ResourceAvailability availability = new ResourceAvailability();
            availability.setHost(dh);
            availability.setAmountOfContainer(usage.getAmountOfContainer());
            availability.setCpuCores(dh.getCores() - usage.getCpuCores());
            availability.setRam(dh.getRam() - usage.getRam());
            availability.setStorage(dh.getStorage() - usage.getStorage());
            freeResources.add(availability);

        }

        return freeResources;
    }


    /**
     * utility function optimization
     */
    public DockerHost selectSuitableHostforContainer(DockerContainer dc, DockerHost blacklistedHost) {
        Double value = Double.MAX_VALUE;
        DockerHost selectedHost = null;

        for (ResourceAvailability ra : calculateFreeResourcesforHosts(blacklistedHost)) {
            Double currentValue = Math.min(ra.getCpuCores()/dc.getCpuCores(), ra.getRam()/dc.getRam());

            if (currentValue < 1) {
                continue;
            }

            if (!ra.getHost().getAvailableImages().contains(dc.getImage())) {
                value = value * 100;
            }

            if (currentValue < value) {
                if (value > 0) {
                    value = currentValue;
                    selectedHost = ra.getHost();
                }
            }
        }

        return selectedHost;
    }



    public String selectServiceTobeScaledDown() {
        Double value = Double.MIN_VALUE;
        String selectedOperator = null;

        //get all Instances
        Integer maxInstances = Integer.MIN_VALUE;
        Integer minInstances = 0;
        Map<String, Integer> operatorAmount = new HashMap<>();

        for(String operator: tmgmt.getOperatorsAsList()) {
            Integer amount = dcr.findByOperator(operator).size();
            operatorAmount.put(operator, dcr.findByOperator(operator).size());
            if (amount>maxInstances) {
                maxInstances = amount;
            }
            if ((amount<minInstances) && (amount>0)) {
                minInstances = amount;
            }
        }

        if (maxInstances < 2) {
            return null;
        }

        Map<String, Integer> instancesValue = new HashMap<>();

        for (Map.Entry<String, Integer> entry : operatorAmount.entrySet()) {
            instancesValue.put(entry.getKey(), ((entry.getValue()-minInstances) / (maxInstances-minInstances)));
        }

        //TODO implemented affected instances


        Map<String, Double> delayValues = new HashMap<>();

        for(String operator: tmgmt.getOperatorsAsList()) {
            List <ProcessingDuration> pds = pcr.findFirst5ByOperatorOrderByIdDesc(operator);

            Double avgDuration = 0.0;
            for (ProcessingDuration pd : pds) {
                avgDuration+=pd.getDuration();
            }

            avgDuration = avgDuration/5;

            delayValues.put(operator, (avgDuration/(opConf.getDurationSLA(operator) * relaxationfactor) * ( 1 + penaltycosts)));
        }


        Long totalScalingActions = scr.count();
        Map<String, Long> scalingActions = new HashMap<>();
        for(String operator: tmgmt.getOperatorsAsList()) {
            scalingActions.put(operator, scr.countByOperator(operator)/totalScalingActions);
        }

        Double selectionValue = 0.0;
        for (Map.Entry<String, Integer> entry : instancesValue.entrySet()) {
            String op = entry.getKey();
            if (operatorAmount.get(op)<2) {
                continue;
            }

            value = instancesValue.get(op) * 3 - delayValues.get(op) - scalingActions.get(op) * 0.5;

            if (value < 0) {
                continue;
            }

            if (value > selectionValue) {
                value = selectionValue;
                selectedOperator = op;
            }

        }

            return selectedOperator;

    }



}