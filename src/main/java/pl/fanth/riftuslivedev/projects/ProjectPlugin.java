package pl.fanth.riftuslivedev.projects;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import core.com.rylinaux.plugman.plugins.PluginManager;
import io.socket.client.Ack;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.api.RiftusAPIClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ProjectPlugin {
    private final RiftusAPIClient client;
    private final RiftusAPIClient.ProjectInfo projectInfo;
    private final Socket socket;
    private String pluginName = null;
    // This is used to prevent writing to file which content changed by something else
    private final Map<Path, byte[]> fileReadHashes = new ConcurrentHashMap<>();

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
                //RiftusLiveDev.instance().getLogger().info(Arrays.toString(args));
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

            socket1.on("sendCommand", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    Ack ack = (Ack) args[1];
                    String command = data.getString("command");

                    RiftusLiveDev.instance().getLogger().info("Received command to execute: " + command);

                    Bukkit.getScheduler().runTask(RiftusLiveDev.instance(), () -> {
                        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                        JSONObject response = new JSONObject();
                        try {
                            response.put("success", success);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }

                        ack.call(response);
                    });
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });

            socket1.on("readLogs", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    Ack ack = (Ack) args[1];
                    int numLines = data.getInt("numLines");

                    Path logFile = Paths.get("logs/latest.log");
                    List<String> lines = Files.readAllLines(logFile);

                    // Take only the last numLines lines
                    int fromIndex = Math.max(0, lines.size() - numLines);
                    List<String> actualLines = lines.subList(fromIndex, lines.size());

                    JSONObject response = new JSONObject();
                    response.put("logs", String.join("\n", actualLines));

                    ack.call(response);
                } catch (JSONException | IOException e) {
                    throw new RuntimeException(e);
                }
            });

            socket1.on("readFile", args -> {
                JSONObject data = (JSONObject) args[0];
                Ack ack = (Ack) args[1];
                JSONObject response = new JSONObject();
                try {
                    Path target = resolveServerPath(data.getString("path"));
                    String content = Files.readString(target);
                    this.fileReadHashes.put(target, hashContent(content));
                    response.put("success", true);
                    response.put("content", content);
                } catch (Exception e) {
                    putError(response, e);
                }
                ack.call(response);
            });

            socket1.on("writeFile", args -> {
                JSONObject data = (JSONObject) args[0];
                Ack ack = (Ack) args[1];
                JSONObject response = new JSONObject();
                try {
                    Path target = resolveServerPath(data.getString("path"));

                    String oldContent = "";
                    boolean existed = Files.exists(target);

                    // Refuse to overwrite a file whose content changed since it was last read
                    if (existed) {
                        oldContent = Files.readString(target);
                        byte[] currentHash = hashContent(oldContent);
                        byte[] expectedHash = this.fileReadHashes.get(target);
                        if (expectedHash == null) {
                            response.put("success", false);
                            response.put("error", "The file was not read. Read the file first.");
                            ack.call(response);
                            return;
                        }
                        if (!Arrays.equals(expectedHash, currentHash)) {
                            response.put("success", false);
                            response.put("error", "File content changed since it was last read. Read it again.");
                            ack.call(response);
                            return;
                        }
                    }

                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    String content = data.getString("content");
                    Files.writeString(target, content);
                    this.fileReadHashes.put(target, hashContent(content));
                    response.put("success", true);
                    response.put("diff", generateDiff(oldContent, content));
                } catch (Exception e) {
                    putError(response, e);
                }
                ack.call(response);
            });

            socket1.on("listFiles", args -> {
                JSONObject data = (JSONObject) args[0];
                Ack ack = (Ack) args[1];
                JSONObject response = new JSONObject();
                try {
                    Path target = resolveServerPath(data.getString("path"));
                    JSONArray entries = new JSONArray();
                    try (var stream = Files.list(target)) {
                        for (Path entry : (Iterable<Path>) stream::iterator) {
                            JSONObject entryObject = new JSONObject();
                            boolean isDirectory = Files.isDirectory(entry);
                            entryObject.put("name", entry.getFileName().toString());
                            entryObject.put("isDirectory", isDirectory);
                            entryObject.put("size", isDirectory ? 0 : Files.size(entry));
                            entries.put(entryObject);
                        }
                    }
                    response.put("success", true);
                    response.put("entries", entries);
                } catch (Exception e) {
                    putError(response, e);
                }
                ack.call(response);
            });

            socket1.on("deleteFile", args -> {
                JSONObject data = (JSONObject) args[0];
                Ack ack = (Ack) args[1];
                JSONObject response = new JSONObject();
                try {
                    Path target = resolveServerPath(data.getString("path"));
                    Files.delete(target);
                    this.fileReadHashes.remove(target);
                    response.put("success", true);
                } catch (Exception e) {
                    putError(response, e);
                }
                ack.call(response);
            });

            socket1.connect();

            return socket1;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Resolves a client-supplied path against the server root and prevents escaping it (path traversal).
    private Path resolveServerPath(String relativePath) throws IOException {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path is outside of the server directory");
        }
        return resolved;
    }

    /**
     * Builds a unified diff between the old and new content of a written file.
     *
     * @param oldContent The content before the write (empty if the file did not exist)
     * @param newContent The content after the write
     * @return The unified diff, or an empty string if there are no changes
     */
    private String generateDiff(String oldContent, String newContent) {
        // Remove carriage return
        oldContent = oldContent.replace("\r", "");
        newContent = newContent.replace("\r", "");

        List<String> oldLines = oldContent.isEmpty() ? List.of() : Arrays.asList(oldContent.split("\n", -1));
        List<String> newLines = newContent.isEmpty() ? List.of() : Arrays.asList(newContent.split("\n", -1));

        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(null, null, oldLines, patch, 3);

        return String.join("\n", unifiedDiff);
    }

    /**
     * Computes a SHA-256 hex hash of the given content
     *
     * @param content The file content
     * @return The SHA-256 hash
     */
    private byte[] hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void putError(JSONObject response, Exception e) {
        try {
            response.put("success", false);
            response.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (JSONException ignored) {
            // Should never happen with plain string values
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
                Plugin plugin = Bukkit.getPluginManager().getPlugin(plugman.getFilePluginMap().get(this.getArtifactPath().getFileName().toString()));
                this.pluginName = plugin.getName();

                RiftusLiveDev.instance().getLogger().info("Loaded plugin: " + this.pluginName);
            } catch (Exception e) {
                RiftusLiveDev.instance().getLogger().log(Level.SEVERE, "Failed to load plugin", e);
            }
        } else {
            // Load normally on server startup
            try {
                Plugin plugin = Bukkit.getPluginManager().loadPlugin(this.getArtifactPath().toFile());
                this.pluginName = plugin.getName();
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
        if (this.pluginName != null) {
            core.com.rylinaux.plugman.plugins.Plugin plugmanPlugin = plugmanPluginManager.getPluginByName(this.pluginName);
            plugmanPluginManager.unload(plugmanPlugin);
            RiftusLiveDev.instance().getLogger().info("Unloaded plugin: " + this.pluginName);
            this.pluginName = null;
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

    public String pluginName() {
        return this.pluginName;
    }
}
