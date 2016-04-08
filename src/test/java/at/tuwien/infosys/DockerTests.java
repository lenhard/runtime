package at.tuwien.infosys;

import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.entities.DockerContainer;
import at.tuwien.infosys.processingNodeDeployment.DockerContainerManagement;
import at.tuwien.infosys.processingNodeDeployment.ProcessingNodeManagement;
import at.tuwien.infosys.utility.Utilities;
import com.spotify.docker.client.DockerException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = VispApplication.class)
public class DockerTests {

    @Autowired
    private DockerContainerManagement dcm;

    @Autowired
    private ProcessingNodeManagement pcm;

    @Autowired
    private DockerContainerRepository dcr;

    @Autowired
    private Utilities utility;

    private static final Logger LOG = LoggerFactory.getLogger(DockerTests.class);

    @Ignore
    @Test
    public void startupContainer() throws DockerException, InterruptedException {
        dcm.startContainer("http://128.130.172.224:2375", "monitor", "http://128.130.172.225");
    }

    @Ignore
    @Test
    public void initializeTopology() throws DockerException, InterruptedException {
        utility.initializeTopology("http://128.130.172.224:2375", "http://128.130.172.225");
    }

    @Test
    public void cleanupImages() throws DockerException, InterruptedException {
        utility.cleanupContainer();
    }

    @Test
    public void scalingTest() {
        pcm.scaleup("speed", "http://128.130.172.224:2375", "http://128.130.172.225");
        for (DockerContainer dc : dcr.findByOperator("speed")) {
            LOG.info(dc.toString());
        }
        pcm.scaleDown("speed");
        for (DockerContainer dc : dcr.findByOperator("speed")) {
            LOG.info(dc.toString());
        }
    }

    //TODO test actual scaledown with wait until the container is really gone
    @Test
    public void scaledown() {
        pcm.scaleDown("monitor");
    }

}
