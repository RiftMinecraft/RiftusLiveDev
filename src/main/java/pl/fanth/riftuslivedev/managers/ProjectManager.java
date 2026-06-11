package pl.fanth.riftuslivedev.managers;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ProjectManager {
    private static final Map<String, ProjectPlugin> PROJECT_PLUGIN_MAP = new HashMap<>(); // project name -> plugin

    @Blocking
    public static ProjectPlugin addLiveKeyAndLoad(String liveKey, boolean serverStartup) {
        ProjectPlugin projectPlugin = new ProjectPlugin(liveKey);
        PROJECT_PLUGIN_MAP.put(projectPlugin.projectInfo().name(), projectPlugin);
        projectPlugin.downloadAndLoadPlugin(serverStartup);

        return projectPlugin;
    }

    public static Collection<ProjectPlugin> getProjectPlugins() {
        return PROJECT_PLUGIN_MAP.values();
    }

    @Nullable
    public static ProjectPlugin getByName(String name) {
        return PROJECT_PLUGIN_MAP.get(name);
    }

    public static void removeProjectPlugin(String name) {
        PROJECT_PLUGIN_MAP.remove(name);
    }

    public static Set<String> getProjectNames() {
        return PROJECT_PLUGIN_MAP.keySet();
    }
}
