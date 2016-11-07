package at.tuwien.infosys.reasoner.rl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reled.RLController;
import reled.ReLEDParameters;
import reled.learning.entity.Action;
import reled.learning.entity.ActionAvailable;
import reled.model.Application;
import reled.model.ApplicationSLA;
import reled.model.Operator;
import at.tuwien.infosys.configuration.OperatorConfiguration;
import at.tuwien.infosys.datasources.ApplicationQoSMetricsRepository;
import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.datasources.DockerHostRepository;
import at.tuwien.infosys.datasources.OperatorQoSMetricsRepository;
import at.tuwien.infosys.datasources.OperatorReplicationReportRepository;
import at.tuwien.infosys.datasources.ScalingActivityRepository;
import at.tuwien.infosys.entities.ApplicationQoSMetrics;
import at.tuwien.infosys.entities.DockerContainer;
import at.tuwien.infosys.entities.DockerHost;
import at.tuwien.infosys.entities.OperatorQoSMetrics;
import at.tuwien.infosys.entities.OperatorReplicationReport;
import at.tuwien.infosys.entities.ResourceAvailability;
import at.tuwien.infosys.entities.ScalingActivity;
import at.tuwien.infosys.monitoring.AvailabilityWatchdog;
import at.tuwien.infosys.reasoner.ReasonerUtility;
import at.tuwien.infosys.reasoner.rl.internal.ApplicationModelBuilder;
import at.tuwien.infosys.reasoner.rl.internal.LeastLoadedHostFirstComparator;
import at.tuwien.infosys.reasoner.rl.internal.LoadBalancingPlacementStrategy;
import at.tuwien.infosys.reasoner.rl.internal.OperatorModelBuilder;
import at.tuwien.infosys.reasoner.rl.internal.PlacementStrategy;
import at.tuwien.infosys.reasoner.rl.internal.RLParameterManager;
import at.tuwien.infosys.reasoner.rl.internal.SortedList;
import at.tuwien.infosys.resourceManagement.ProcessingNodeManagement;
import at.tuwien.infosys.resourceManagement.ResourceProvider;
import at.tuwien.infosys.topology.TopologyManagement;

@Service
public class CentralizedRLReasoner {
    
	/* *** Constants 				*** */
	/* Currently, VISP-Runtime manages a single application */
	public static final String APPNAME = "default";
	public static final int FIRST = 0;
	
	public static final boolean DEBUG = true;
	
	/* *** Auto-wired Dependencies 	*** */
	@Autowired
	private TopologyManagement topologyManager;
	
    @Autowired
    private OperatorConfiguration operatorConfig;
    
    @Autowired
    private ProcessingNodeManagement procNodeManager;
    
    @Autowired
    private DockerContainerRepository dockerRepository;
    
    @Autowired
    private DockerHostRepository dockerHostRepository;
    
    @Autowired
    private ApplicationQoSMetricsRepository applicationMetricsRepository;
    
    @Autowired
    private OperatorQoSMetricsRepository operatorMetricsRepository;
    
    @Autowired
    private RLParameterManager paramManager;

    @Autowired
    private ResourceProvider resourceProvider;
    
    @Autowired
    private AvailabilityWatchdog availabilityWatchdog;

    @Autowired
    private ReasonerUtility reasonerUtility;

    @Autowired
    private ScalingActivityRepository scalingActivityRepository;
    
    @Autowired
    private OperatorReplicationReportRepository operatorReplicationRepository;
    
    @Value("${visp.infrastructurehost}")
    private String infrastructureHost;
    
    @Value("${reasoner.evaluate.afterrounds}")
    private Integer waitForReconfigurationEffects;
    
    @Value("#{'${application.pinned}'.split(',')}")
    private List<String> pinnedOperators;

    private static final Logger LOG = LoggerFactory.getLogger(CentralizedRLReasoner.class);

    /* *** Internal attributes 		*** */
    private Application application; 
    private ApplicationSLA applicationSla; 
	private Map<String, RLController> controller;
	private Map<String, Operator> operatorNameToOperator;
	private Map<String, Integer> cooldownOperators;

	private long MEASUREMENTS_EXPIRATION_INTERVAL = 5 * 60 * 1000;
	
	public CentralizedRLReasoner() {
	}
	
	public void initialize(){

		/* Centralized Version of ReLED Controller */
		controller = new HashMap<String, RLController>();
		operatorNameToOperator = new HashMap<String, Operator>();
		cooldownOperators = new HashMap<String, Integer>();
		
		/* Generate a new RL Controller for each operator */
		createRLController();
	
		/* Retrieve the application SLA */
		createApplicationSla();
		
	}
	

	private void createRLController(){
		
		/* Create Parameters for RL Controllers */
		ReLEDParameters params = paramManager.getReLEDParameters();
		
		/* Create RL Controllers */
		for (String operator : topologyManager.getOperatorsAsList()){
			controller.put(operator, new RLController(params));
		}
		
	}
	private void createApplicationSla(){
		
		/* Collect Application QoS Requirements */
		applicationSla = new ApplicationSLA(APPNAME);
		applicationSla.setMaxResponseTime(paramManager.getMaxResponseTime());
		applicationSla.setMaxUtilization(paramManager.getMaxUtilization());
		applicationSla.setMinThroughtput(paramManager.getMinThroughput());
		
		LOG.info("Application SLA: " + applicationSla);
		
	}
	
	public void runAdaptationCycle(){
				
		/* Terminate container and hosts that have been flagged 
		 * to shutdown in the previous adaptation cycles */
		if (DEBUG){
			LOG.info(" ============================================================= .");
			LOG.info(". Terminating Containers and Host if flagged.");
		}
		terminateFlaggedContainersAndHosts();
		
//		if (DEBUG)
//			LOG.info(". Try to consolidate container");
//		consolidateContainers();

		/* Update operators in a cooldown state */
		updateCoolingDownOperators();

		if (DEBUG)
			LOG.info(". Pinned Operators: " + pinnedOperators);
		
		/* Execute MAPE control cycle */
		try {

			if (DEBUG)
				LOG.info(". Monitoring...");
			monitor();

			/* Report Operators Replication and Stats */
			saveOperatorReplication();

			/* ************** DEBUG ************** */
			if (DEBUG){
				LOG.info(". Monitored Application: " + application.getApplicationId() + " respTime="+ application.getResponseTime());
				if (!operatorNameToOperator.isEmpty())
					for(Operator o : operatorNameToOperator.values()){
						int repl = 0;
						double avgUtil = 0.0;
						if (o.getDeployment() != null){
							repl = o.getDeployment().getResources().size();
							avgUtil = o.getDeployment().getAverageUtilization();
						}
						LOG.info(". Monitored Operator: " + o.getOperatorId() + " - repl:" + repl + ", u:" + avgUtil + " - p:" + o.getProcessedMessagesPerUnitTime() + " r:"+ o.getReceivedMessagesPerUnitTime());
					}
			}
			/* ************** DEBUG ************** */
			
			
			/* Run -APE steps for each operator (in a concurrent way) */
			for (String operatorName : topologyManager.getOperatorsAsList()) {
				
				if (DEBUG)
					LOG.info(". Operator: " + operatorName);
				
				/* Save RL information */
				saveQ(operatorName);
				saveStateVisits(operatorName);
				
		    	/* Do not scale pinned or cooling-down operators */
				if (!canReconfigure(operatorName))
					continue;
				
				try{
					
					Action action = analyzeAndPlan(operatorName);
					if (DEBUG){
						LOG.info(". Analyze: Next action for " + operatorName + " " + action.getAction());
						LOG.info(". Execute: " + action.getAction());
					}
					
					execute(operatorName, action);
			
				}catch(RLException apeExc){
					LOG.error(apeExc.getMessage());
					apeExc.printStackTrace();
				}
				
			}
			
		} catch (RLException rle) {
			LOG.error(rle.getMessage());
			rle.printStackTrace();
		}

		if (DEBUG)
			LOG.info(" ============================================================= .");

	}
	
	
	private void terminateFlaggedContainersAndHosts(){
		availabilityWatchdog.checkAvailablitiyOfContainer();
		procNodeManager.removeContainerWhichAreFlaggedToShutdown();
		resourceProvider.get().removeHostsWhichAreFlaggedToShutdown();
	}
	
	/**
	 * Retrieve all metrics related to the operators
	 * @throws Exception 
	 */
	private void monitor() throws RLException{

		/* Create Application from monitored data:
		 *  - (end-to-end) average response time */
		application = createApplicationModel();
		if (application == null)
			throw new RLException("Application Model not available");
		
		
		/* Create Operator from monitored data: 
		 * 	- Operator: receivedMsg/s; processedMsg/s 
		 *  - Resources: averageCpuUsage
		 *  	- Resource: containerId, cpuUsage
		 */
		List<String> operatorNames = topologyManager.getOperatorsAsList();
		if (operatorNames == null || operatorNames.isEmpty())
			throw new RLException("Operator Model not available");
		
		for (String operatorName : topologyManager.getOperatorsAsList()) {
			Operator operator = createOperatorModel(operatorName);
			operatorNameToOperator.put(operatorName, operator);
		}

	}
	
	/**
	 * Analyze monitored metrics and plan next action for the operator node
	 * @param operatorName
	 * @return
	 * @throws Exception
	 */
	private Action analyzeAndPlan(String operatorName) throws RLException{
		
		RLController operatorController = controller.get(operatorName);

		Operator operator = operatorNameToOperator.get(operatorName);
		
		if (operatorController == null || application == null || operator == null)
			throw new RLException("Invalid operator information (controller, application, operator)");
		
		// DEBUG: 
		double reward = operatorController.getReward(application, operator, applicationSla);
		LOG.info(" Last Action reward = " + reward);
		saveReward(operatorName, reward);
		
		Action action = operatorController.nextAction(application, operator, applicationSla);

		while(!isFeasible(operatorName, action)){
			
			LOG.info("Action " + action + " is unfeasible. Computing new action... ");
			operatorController.declareLastActionAsUnfeasible();
			action = operatorController.nextAction(application, operator, applicationSla);
			
		}
		
		return action; 
		
	}
	
	/**
	 * Enact the planned action to the execution environment.
	 * 
	 * If a new docker container has to be instantiated, first try to 
	 * use available resources (hosts) by balancing the number of containers
	 * per host; if no available resource is found, a new one is launched. 
	 * 
	 * @param operatorName
	 * @param action
	 * @throws Exception
	 */
	private void execute(String operatorName, Action action){
		
		ActionAvailable decodedAction = action.getAction();
		
		saveAction(operatorName, action.getValue());
		
		switch(decodedAction){
			case SCALE_IN:
				/* Scale-in the number of operator replicas by stopping a 
				 * (randomly chosen) processing node (Docker container) */
				procNodeManager.scaleDown(operatorName);
				
				/* Track scaling activity */
				/* Action already tracked in procNodeManager.scaleDown() */
				
				/* In this step containers are flagged as "stopping";
				 * the consolidation of containers on available hosts is
				 * postponed to next execution of the adaptation cycle */
				
				/* Put operator in a cooling down state */
				cooldownOperators.put(operatorName, waitForReconfigurationEffects);
				
				break;
				
			case NO_ACTIONS:
				break;
			
			case SCALE_OUT:
				/* Scale-out the number of operator replicas by executing the steps: 
				 * - create a new docker container
				 * - determine the container placement (existing nodes, new node) 
				 * - launch the container */
				DockerContainer container = operatorConfig.createDockerContainerConfiguration(operatorName);
				DockerHost host = determineContainerPlacement(container);
				procNodeManager.scaleup(container, host, infrastructureHost);

				/* Track scaling activity */
				/* Action already tracked in procNodeManager.scaleUp() */

				/* Put operator in a cooling down state */
				cooldownOperators.put(operatorName, waitForReconfigurationEffects);

				break;
		}
		

	}
	
	/**
	 * Determine the container placement (existing nodes or a new one) 
	 * @param DockerContainer
	 * @return DockerHost
	 */
    private synchronized DockerHost determineContainerPlacement(DockerContainer container) {
    	
    	DockerHost candidateHost = null;
    	
    	/* Compute resource availability on each running host */
        List<ResourceAvailability> availableResources = reasonerUtility.calculateFreeResourcesforHosts(candidateHost);

    	/* Try to use an already running host */
        candidateHost = computePlacementWithLoadBalancing(container, availableResources);

    	/* Start a new VM */
        if (candidateHost == null) {
        	candidateHost = resourceProvider.createContainerSkeleton(); 
            return resourceProvider.get().startVM(candidateHost);
        } 

        return candidateHost;
        
    }
    
    
    /**
     * If possible, consolidate running containers 
     * on fewer computing resources. 
     */
    private void consolidateContainers(){
    	
    	/* Avoid turning off the only available host */
    	if (dockerHostRepository.count() <= 1)
    		return;
    	
    	/* Compute resource availability on each running host */
        List<ResourceAvailability> availableResources = reasonerUtility.calculateFreeResourcesforHosts(null);

    	/* Sort hosts in decreasing order w.r.t. the number of hosted containers */
    	SortedList<ResourceAvailability> sortedResources = new SortedList<ResourceAvailability>(new LeastLoadedHostFirstComparator());
    	sortedResources.addAll(availableResources);

		/* Determine host to be turned off and its container relocation */
    	DockerHost hostToTurnOff = null;
    	Map<DockerContainer, DockerHost> relocationMap = null;
    	for(ResourceAvailability resource : sortedResources){

    		/* Check if host (resource) can be turned off and its container safely relocated */
    		relocationMap = reasonerUtility.canRelocateHostedContainers(resource, sortedResources);
    		if (relocationMap != null){
    			hostToTurnOff = resource.getHost();
    			
    			/* if at least a host can be released, stop consolidation */
    	    	break;
    		}
    	}
    	
    	/* Execute relocation, if suitable */
    	if (relocationMap == null){
    		LOG.info(". Nothing to consolidate...");
    		return;
    	}

    	for(DockerContainer container : relocationMap.keySet()){
    		LOG.info(". Consolidation: moving " + container.getContainerid() + " to " + relocationMap.get(container).getName());
    		
    		/* Relocate Container */
    		DockerHost destinationHost = relocationMap.get(container);
    		procNodeManager.triggerShutdown(container);
    		procNodeManager.scaleup(container, destinationHost, infrastructureHost);
    		
    		/* Track consolidation activity */
    		scalingActivityRepository.save(new ScalingActivity("container", 
    				new DateTime(DateTimeZone.UTC).toString(), container.getOperator(), 
    							"consolidation", container.getHost()));

    	}
    	
    	LOG.info(". Host " + hostToTurnOff.getName() + " marked for removal... ");
		
    	/* Release resource */
    	resourceProvider.get().markHostForRemoval(hostToTurnOff);
                	
    }

    private Application createApplicationModel(){

    	ApplicationQoSMetrics qosMetrics = null;
    	List<ApplicationQoSMetrics> metrics = applicationMetricsRepository.findFirstByApplicationNameOrderByTimestampDesc(APPNAME);
    	
    	if (metrics == null || metrics.isEmpty()){
    		qosMetrics = new ApplicationQoSMetrics(Long.toString(System.currentTimeMillis()), APPNAME, 0.0);
    	} else {
    		qosMetrics = metrics.get(FIRST);
    		
    		/* Check if measurement is expired */
	 		long lastMeasurementTimestamp = new Long(qosMetrics.getTimestamp());
	 		if (lastMeasurementTimestamp < System.currentTimeMillis() - MEASUREMENTS_EXPIRATION_INTERVAL){
	 			qosMetrics = new ApplicationQoSMetrics(Long.toString(System.currentTimeMillis()), APPNAME, 0.0);
	 		}
    	}

    	return ApplicationModelBuilder.create(qosMetrics);
    	
    }
    
    private void updateCoolingDownOperators(){

    	if (cooldownOperators == null || cooldownOperators.isEmpty())
    		return;
    	
    	for (String operator : cooldownOperators.keySet()){
    		Integer roundToWait = cooldownOperators.get(operator);
    		
    		if (roundToWait == null || roundToWait.equals(0)){
    			cooldownOperators.remove(operator);
    		}else{
        		roundToWait = new Integer(roundToWait.intValue() - 1);
        		cooldownOperators.put(operator, roundToWait);
    		}
    		
    	}
    	
    }
    
    private boolean canReconfigure(String operatorName){
    	
    	/* Do not scale pinned operators */
		if (pinnedOperators.contains(operatorName.toLowerCase()))
			return false;
		
		/* Do not scale operators in a cooling down state*/
		if (cooldownOperators.containsKey(operatorName))
    		return false;
    	
    	return true;
    	
    }
    
 	private Operator createOperatorModel(String operatorName){

		List<OperatorQoSMetrics> operatorsMetrics = operatorMetricsRepository.findFirstByNameOrderByTimestampDesc(operatorName);
		List<DockerContainer> containers = dockerRepository.findByOperator(operatorName);

	 	OperatorQoSMetrics qosMetrics = null;
	 	if (operatorsMetrics == null || operatorsMetrics.isEmpty()){
	 		qosMetrics = new OperatorQoSMetrics(operatorName, Long.toString(System.currentTimeMillis()), 0.0, 0.0);
	 	} else {
	 		qosMetrics = operatorsMetrics.get(FIRST);

	 		/* Check if measurement is expired */
	 		long lastMeasurementTimestamp = new Long(qosMetrics.getTimestamp());
	 		if (lastMeasurementTimestamp < System.currentTimeMillis() - MEASUREMENTS_EXPIRATION_INTERVAL){
	 			qosMetrics = new OperatorQoSMetrics(operatorName, Long.toString(System.currentTimeMillis()), 0.0, 0.0);
	 		}
	 	}
	 	
	 	return OperatorModelBuilder.create(operatorName, qosMetrics, containers);
	 	
	 }
 	

    private DockerHost computePlacementWithLoadBalancing(DockerContainer container, List<ResourceAvailability> availableResources) {
    	
    	PlacementStrategy ps = new LoadBalancingPlacementStrategy();
    	return ps.computePlacement(container, availableResources);
    	
    }
	 
    private boolean isFeasible(String operatorName, Action action){
    	
    	Operator operator = operatorNameToOperator.get(operatorName);
    	
    	if (operator == null || operator.getDeployment() == null)
    		return false;
    	
    	if (operator.getDeployment().getResources().size() < 2 && 
    			ActionAvailable.SCALE_IN.equals(action.getAction()))
    			return false;
    	
    	return true;
    }
 
    
    private void saveOperatorReplication(){

    	if (operatorNameToOperator.isEmpty())
			return;
		
    	for(Operator o : operatorNameToOperator.values()){
    		
    		try{

    			OperatorReplicationReport report = new OperatorReplicationReport(o.getOperatorId(), 
        				Long.toString(System.currentTimeMillis()), o.getDeployment());
            	operatorReplicationRepository.save(report);
    			
    		} catch (Exception e){
    			
    		}
    		
		}
    	
    }

    private void saveQ(String operatorName){
    	
    	RLController ctr = controller.get(operatorName);
    	
    	String state = ctr.qStateAsString();
    	
    	File f = new File("Qtable_" + operatorName);
    	try {
    		if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter fw = new FileWriter(f.getAbsoluteFile());
			PrintWriter pw = new PrintWriter(fw);
			pw.write(state);
			pw.flush(); 
			pw.close();
			fw.close();
    	} catch (IOException e) {
			e.printStackTrace();
		}
    	
    }

    
    private void saveStateVisits(String operatorName){
		
		RLController ctr = controller.get(operatorName);
		String state = ctr.stateVisitsAsString();
		
		File f = new File("Visits_" + operatorName);
		try {
			if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter fw = new FileWriter(f.getAbsoluteFile());
			PrintWriter pw = new PrintWriter(fw);
			pw.write(state);
			pw.flush(); 
			pw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

    private void saveReward(String operatorName, double reward){
		
		String output = System.currentTimeMillis() + ", " + reward + "\n";
		
		File f = new File("Reward_" + operatorName);
		try {
			if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter fw = new FileWriter(f.getAbsoluteFile(), true);
			PrintWriter pw = new PrintWriter(fw);
			pw.write(output);
			pw.flush(); 
			pw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

    private void saveAction(String operatorName, int actionCode){
		
		String output = System.currentTimeMillis() + ", " + actionCode + "\n";
		
		File f = new File("Action_" + operatorName);
		try {
			if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter fw = new FileWriter(f.getAbsoluteFile(), true);
			PrintWriter pw = new PrintWriter(fw);
			pw.write(output);
			pw.flush(); 
			pw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

}
