package at.tuwien.infosys.reasoner;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import at.tuwien.infosys.reasoner.rl.CentralizedRLReasoner;

@Service
public class ReasonerRL {

    @Autowired
    private CentralizedRLReasoner rlReasoner; 
    
    private static final Logger LOG = LoggerFactory.getLogger(ReasonerRL.class);

    @PostConstruct
    public void init() {

    	LOG.info("Initializing RLReasoner");
    	rlReasoner.initialize();
    	
    }
    
    @Scheduled(fixedRateString = "${visp.reasoning.timespan}")
    public synchronized void updateResourceconfiguration() {

    	LOG.info(" + Run Adaptation Cycle");
    	rlReasoner.runAdaptationCycle();
    
    }
    
}
