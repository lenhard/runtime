package at.tuwien.infosys.datasources;


import at.tuwien.infosys.entities.PooledVM;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PooledVMRepository extends CrudRepository<PooledVM, Long> {

    PooledVM findFirstByName(String name);
    PooledVM findFirstByPoolnameAndLinkedhostIsNull(String poolname);
    List<PooledVM> findByPoolName(String poolname);

}