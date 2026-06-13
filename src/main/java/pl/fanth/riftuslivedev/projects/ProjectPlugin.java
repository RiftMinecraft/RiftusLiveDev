package pl.fanth.riftuslivedev.projects;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import core.com.rylinaux.plugman.plugins.PluginManager;
import io.socket.client.IO;
import io.socket.client.Socket;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.api.RiftusAPIClient;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ProjectPlugin {
    private final RiftusAPIClient client;
    private final RiftusAPIClient.ProjectInfo projectInfo;
    private final Socket socket;
    private Plugin plugin;

    public ProjectPlugin(RiftusAPIClient client, RiftusAPIClient.ProjectInfo projectInfo) {
        this.client = client;
        this.projectInfo = projectInfo;
        this.socket = this.setupSocketIO();
    }

    private Socket setupSocketIO() {
        try {
            IO.Options options = IO.Options.builder()
                .setExtraHeaders(Map.of(
                    "x-live-key", List.of(this.client.liveKey())
                ))
                .build();

            Socket socket1 = IO.socket(RiftusAPIClient.getBaseUrl(), options);
            socket1.on(Socket.EVENT_CONNECT, args -> {
                RiftusLiveDev.instance().getLogger().info("WebSocket connected for " + projectInfo.name());
                RiftusLiveDev.instance().getLogger().info(Arrays.toString(args));
            });
            socket1.on(Socket.EVENT_CONNECT_ERROR, args -> {
                RiftusLiveDev.instance().getLogger().severe("WebSocket connect error for " + projectInfo.name());
                if (args[0] instanceof Exception ex) {
                    RiftusLiveDev.instance().getLogger().log(Level.SEVERE, "Error", ex);
                }
                RiftusLiveDev.instance().getLogger().info(Arrays.toString(args));
            });
            socket1.on(Socket.EVENT_DISCONNECT, args -> {
                RiftusLiveDev.instance().getLogger().info("WebSocket disconnected for " + projectInfo.name());
            });

            socket1.on("artifactUpdated", args -> {
                System.out.println(Arrays.toString(args));

                Bukkit.broadcast(Component.text("Received update for " + this.projectInfo().name() + "...").color(NamedTextColor.GREEN), "riftuslivedev.broadcastupdate");
                this.downloadAndLoadPlugin(false);
                Bukkit.broadcast(Component.text("Plugin " + this.projectInfo().name() + " has been updated!").color(NamedTextColor.GREEN), "riftuslivedev.broadcastupdate");

                // Play sound
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("riftuslivedev.broadcastupdate")) {
                        player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 2.0f));
                    }
                }
            });

            socket1.connect();

            return socket1;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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

    public void shutdown() {
        this.socket.close();
        this.unloadPlugin();
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

    public static String getWSBaseUrl() {
        if (!RiftusLiveDev.instance().pluginConfiguration().dev) {
            return "wss://riftus-api.fanth.pl";
        } else {
            return "ws://localhost:3000";
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
}
