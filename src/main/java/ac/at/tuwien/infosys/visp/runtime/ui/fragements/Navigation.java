package ac.at.tuwien.infosys.visp.runtime.ui.fragements;

import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class Navigation {

    public List<NavEntry> getNavEntries() {
        return Arrays.asList(
                new NavEntry("/", "about", "About"),
                new NavEntry("/topology", "changeTopology", "Topology Configuration"),
                new NavEntry("/pooledvms", "pooledvms", "Resource Pools"),
                new NavEntry("/console", "consoleoutput", "Console"),
                new NavEntry("/configuration", "configuration", "Configuration")
                );
    }

    private static class NavEntry {
        private final String url, viewName, userFriendlyName;

        NavEntry(String url, String viewName, String userFriendlyName) {
            this.url = url;
            this.viewName = viewName;
            this.userFriendlyName = userFriendlyName;
        }

        public String getUrl() {
            return url;
        }

        public String getViewName() {
            return viewName;
        }

        public String getUserFriendlyName() {
            return userFriendlyName;
        }
    }
}
