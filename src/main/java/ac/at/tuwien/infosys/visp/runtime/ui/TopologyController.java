package ac.at.tuwien.infosys.visp.runtime.ui;


import ac.at.tuwien.infosys.visp.runtime.topology.TopologyManagement;
import ac.at.tuwien.infosys.visp.runtime.topology.TopologyUpdateHandler;
import ac.at.tuwien.infosys.visp.runtime.topology.rabbitMq.UpdateResult;
import ac.at.tuwien.infosys.visp.runtime.utility.Utilities;
import org.apache.commons.io.IOUtils;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Controller
public class TopologyController {

    @Autowired
    private TopologyUpdateHandler topologyUpdateHandler;

    @Autowired
    private TopologyManagement topologyManagement;

    @Autowired
    private Utilities utilities;

    @Value("${visp.runtime.ip}")
    private String runtimeip;

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @RequestMapping("/topology")
    public String index(Model model) throws SchedulerException {
        model.addAttribute("pagetitle", "VISP Runtime - " + runtimeip);

        if(topologyManagement.getTopology().size() == 0) {
            model.addAttribute("emptyTopology", true);
        } else {
            model.addAttribute("emptyTopology", false);
            try {
                model.addAttribute("dotContent", getTopologyForVizJs(topologyManagement.getDotfile()));
            } catch (Exception e) {
                LOG.error("Unable to load graphviz image", e);
            }
        }
        return "changeTopology";
    }

    private String getTopologyForVizJs(String dotFilePath) throws IOException {
        String dotContent = new String(Files.readAllBytes(Paths.get(dotFilePath)));
        dotContent = dotContent.replaceAll("[\\t\\n\\r]"," ");
        dotContent = dotContent.replaceAll("\"","\\\"");
        return dotContent;
    }

    @RequestMapping(value = "/topology/uploadTopologyGUI", method = RequestMethod.POST)
    public String uploadTopology(Model model,
            @RequestParam("file") MultipartFile file) {
        /**
         * this method is used to upload an updated topology description file by the user
         */

        model.addAttribute("pagetitle", "VISP Runtime - " + runtimeip);
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(file.getBytes());
            String fileContent = IOUtils.toString(stream, "UTF-8");
            UpdateResult result = topologyUpdateHandler.handleUpdateFromUser(fileContent);
            model.addAttribute("updateResult", result);
            if (result.dotPath != null && result.getStatus() == UpdateResult.UpdateStatus.SUCCESSFUL) {
                model.addAttribute("dotContent", getTopologyForVizJs(result.dotPath));
            } else {
                model.addAttribute("graphvizAvailable", false);
            }
        }
        catch (Exception e) {
            LOG.error(e.getLocalizedMessage());
        }

        return "afterTopologyUpdate";
    }

    @RequestMapping("/topology/clear")
    public String reinitialize(Model model) throws SchedulerException {

        //TODO propagate the deletions also to all other VISP instances - this operation is a hard reset and also
        //removed the docker containers there

        utilities.createInitialStatus();

        model.addAttribute("pagetitle", "VISP Runtime - " + runtimeip);
        return "afterTopologyUpdate";
    }


}
