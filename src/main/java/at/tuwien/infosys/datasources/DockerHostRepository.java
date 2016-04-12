package at.tuwien.infosys.datasources;


import at.tuwien.infosys.entities.DockerHost;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DockerHostRepository extends CrudRepository<DockerHost, Long> {

    List<DockerHost> findByName(String name);
    List<DockerHost> findByUrl(String url);
}
