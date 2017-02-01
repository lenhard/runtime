package at.tuwien.infosys.resourceManagement;

import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.datasources.DockerHostRepository;
import at.tuwien.infosys.datasources.PooledVMRepository;
import at.tuwien.infosys.datasources.entities.DockerContainer;
import at.tuwien.infosys.datasources.entities.DockerHost;
import at.tuwien.infosys.datasources.entities.PooledVM;
import org.jvnet.hk2.annotations.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ResourceUsage {

    @Autowired
    private PooledVMRepository pvmr;

    @Autowired
    private DockerHostRepository dhr;

    @Autowired
    private DockerContainerRepository dcr;

    public void calculateUsageForPool(String resourcePoolName) {

        Double overallCores = 0.0;
        Integer overallMemory = 0;
        Float overallStorage = 0.0F;

        Double plannedCoresUsage = 0.0;
        Integer plannedMemoryUsage = 0;
        Float plannedStorageUsage = 0.0F;

        Double actualCoresUsage = 0.0;
        Long actualMemoryUsage = 0L;
        Float actualStorageUsage = 0.0F;

        for (PooledVM pooledVM : pvmr.findByPoolname(resourcePoolName)) {
            DockerHost dh = dhr.findFirstByName(pooledVM.getLinkedhost());
            overallCores+=dh.getCores();
            overallMemory+=dh.getMemory();
            overallStorage+=dh.getStorage();

            for (DockerContainer dc : dcr.findByHost(dh.getName())) {
                plannedCoresUsage+=dc.getCpuCores();
                plannedMemoryUsage+=dc.getMemory();
                plannedStorageUsage+=dc.getStorage();

                actualCoresUsage+=dc.getCpuUsage();
                actualMemoryUsage+= dc.getMemoryUsage();
            }
        }

        //TODO put the values in a DTO - discuss structure with hiessl

    }


}
