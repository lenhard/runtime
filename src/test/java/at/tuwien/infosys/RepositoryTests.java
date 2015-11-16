package at.tuwien.infosys;

import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.entities.DockerContainer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = VispApplication.class)
public class RepositoryTests {

    @Autowired
    DockerContainerRepository dcr;

    @Test
    public void simpleTest() {
        dcr.save(new DockerContainer("asdf", "host", "image", "operator"));
    }

}
