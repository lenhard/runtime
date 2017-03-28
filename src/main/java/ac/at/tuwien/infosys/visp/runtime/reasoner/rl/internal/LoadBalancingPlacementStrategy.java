package ac.at.tuwien.infosys.visp.runtime.reasoner.rl.internal;

import ac.at.tuwien.infosys.visp.runtime.datasources.entities.DockerHost;
import ac.at.tuwien.infosys.visp.runtime.datasources.entities.DockerContainer;

import java.util.List;

public class LoadBalancingPlacementStrategy implements PlacementStrategy {

	@Override
	public DockerHost computePlacement(DockerContainer container,
                                       List<ResourceAvailability> availableResources) {
		
        SortedList<ResourceAvailability> candidates = new SortedList<>(new LeastLoadedHostFirstComparator());
        
        for (ResourceAvailability ra : availableResources) {
            if (ra.getCores() <= container.getCpuCores()) {
                continue;
            }
            if (ra.getMemory() <= container.getMemory()) {
                continue;
            }
            if (ra.getStorage() <= container.getStorage()) {
                continue;
            }

            candidates.add(ra);
        }
        
        if (candidates.isEmpty())
        	return null;
        
        return candidates.get(0).getHost();

	}
	
}
