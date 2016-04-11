package at.tuwien.infosys.resourceManagement;

import at.tuwien.infosys.datasources.DockerContainerRepository;
import at.tuwien.infosys.datasources.DockerHostRepository;
import at.tuwien.infosys.entities.DockerContainer;
import com.spotify.docker.client.DockerException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ProcessingNodeManagement {

    @Value("${visp.shutdown.graceperiod}")
    private Integer graceperiod;

    @Autowired
    DockerContainerManagement dcm;

    @Autowired
    private DockerContainerRepository dcr;

    @Autowired
    private DockerHostRepository dhr;

    private static final Logger LOG = LoggerFactory.getLogger(ProcessingNodeManagement.class);

    public void removeContainerWhichAreFlaggedToShutdown() {
        for (DockerContainer dc : dcr.findByStatus("stopping")) {
            DateTime now = new DateTime(DateTimeZone.UTC);
            LOG.info("removeContainerWhichAreFlaggedToShutdown shuptdown container (" + dc.getOperator() + ") : current time: " + now + " - " + "termination time:" + new DateTime(dc.getTerminationTime()).plusMinutes(graceperiod));
            if (now.isAfter(new DateTime(dc.getTerminationTime()).plusSeconds(graceperiod))) {
                try {
                    dcm.removeContainer(dc);
                } catch (DockerException e) {
                    LOG.error("Cloud not remove docker Container while houskeeping.", e);
                } catch (InterruptedException e) {
                    LOG.error("Cloud not remove docker Container while houskeeping.", e);
                }
            }
        }
    }

    public void scaleup(String operator, String dockerHost, String infrastructureHost) {
        try {
            dcm.startContainer(dockerHost, operator, infrastructureHost);
        } catch (DockerException e) {
            LOG.error("Could not start a docker container.", e);
        } catch (InterruptedException e) {
            LOG.error("Could not start a docker container.", e);
        }
        LOG.info("VISP - Scale UP " + operator);
    }

    public void scaleDown(String operator) {
        List<DockerContainer> operators = dcr.findByOperator(operator);

        if (operators.size()<2) {
            return;
        }

        for (DockerContainer dc : operators) {
            if (dc.getStatus() == null) {
                dc.setStatus("running");
            }

            if (dc.getStatus().equals("stopping")) {
                LOG.info("VISP - Scale DOWN " + operator + "-" + dc.getContainerid());
                continue;
            }

            triggerShutdown(dc);
                break;
        }
    }

    public void triggerShutdown(DockerContainer dc) {
        try {
            dcm.executeCommand(dc, "cd ~ ; touch killme");
            dcm.markContainerForRemoval(dc);
        } catch (DockerException e) {
            LOG.error("Could not trigger scaledown operation.", e);
        } catch (InterruptedException e) {
            LOG.error("Could not trigger scaledown operation.", e);
        }

    }
}