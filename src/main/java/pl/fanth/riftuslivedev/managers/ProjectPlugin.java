package pl.fanth.riftuslivedev.managers;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import core.com.rylinaux.plugman.plugins.PluginManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Blocking;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.api.RiftusAPIClient;
import pl.fanth.riftuslivedev.api.RiftusWebSocket;

import java.nio.file.Path;
import java.util.logging.Level;

public class ProjectPlugin {
    private final String liveKey;
    private final RiftusAPIClient client;
    private final RiftusAPIClient.ProjectInfo projectInfo;
    private final RiftusWebSocket webSocket;
    private Plugin plugin;

    @Blocking
    public ProjectPlugin(String liveKey) {
        this.liveKey = liveKey;
        this.client = new RiftusAPIClient(liveKey);
        this.projectInfo = this.client.getProjectInfo();
        this.webSocket = new RiftusWebSocket(this);
        this.webSocket.connect();
    }

    public void downloadAndLoadPlugin(boolean serverStartup) {
        RiftusLiveDev.instance().getLogger().info("Loading plugin " + this.projectInfo.name() + "...");

        // Download artifact
        RiftusLiveDev.instance().getLogger().info("Downloading artifact...");
        this.client.downloadArtifact(this.getArtifactPath());
        RiftusLiveDev.instance().getLogger().info("Artifact downloaded");

        // Load plugin
        this.loadPlugin(serverStartup);
    }

    private void loadPlugin(boolean serverStartup) {
        if (!serverStartup) {
            // Use plugman when in the middle of server running

            PlugManBukkit plugman = (PlugManBukkit) Bukkit.getPluginManager().getPlugin("PlugManX");
            PluginManager plugmanPluginManager = plugman.getPluginManager();

            // Unload
            this.unloadPlugin();

            // Load
            try {
                // Remove .jar
                String pathForPlugman = RiftusLiveDev.instance().getDataPath().getParent().relativize(this.getArtifactPath()).toString();
                if (pathForPlugman.endsWith(".jar")) {
                    pathForPlugman = pathForPlugman.substring(0, pathForPlugman.length() - 4);
                }
                //System.out.println("pathForPlugman: " + pathForPlugman);

                plugmanPluginManager.load(pathForPlugman);
                this.plugin = Bukkit.getPluginManager().getPlugin(plugman.getFilePluginMap().get(this.getArtifactPath().getFileName().toString()));

                RiftusLiveDev.instance().getLogger().info("Loaded plugin: " + this.plugin.getName());
            } catch (Exception e) {
                RiftusLiveDev.instance().getLogger().log(Level.SEVERE, "Failed to load plugin", e);
            }
        } else {
            // Load normally on server startup
            try {
                this.plugin = Bukkit.getPluginManager().loadPlugin(this.getArtifactPath().toFile());
            } catch (InvalidPluginException | InvalidDescriptionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void unloadPlugin() {
        // Unload
        PlugManBukkit plugman = (PlugManBukkit) Bukkit.getPluginManager().getPlugin("PlugManX");
        PluginManager plugmanPluginManager = plugman.getPluginManager();
        if (this.plugin != null) {
            core.com.rylinaux.plugman.plugins.Plugin plugmanPlugin = plugmanPluginManager.getPluginByName(this.plugin.getName());
            plugmanPluginManager.unload(plugmanPlugin);
            RiftusLiveDev.instance().getLogger().info("Unloaded plugin: " + this.plugin.getName());
            this.plugin = null;
        }
    }

    public Path getArtifactPath() {
        return RiftusLiveDev.instance().getDataPath().resolve("artifacts/" + this.projectInfo.name() + ".jar");
    }

    public RiftusAPIClient client() {
        return client;
    }

    public RiftusAPIClient.ProjectInfo projectInfo() {
        return projectInfo;
    }

    public Plugin plugin() {
        return plugin;
    }

    public String liveKey() {
        return liveKey;
    }
}
