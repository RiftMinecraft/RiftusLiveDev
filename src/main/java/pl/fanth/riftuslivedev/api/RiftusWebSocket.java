package pl.fanth.riftuslivedev.api;

import com.google.gson.Gson;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.managers.ProjectPlugin;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class RiftusWebSocket extends WebSocketClient {
    private static final Gson GSON = new Gson();
    private static final long RECONNECT_DELAY_MS = 10_000;
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 60;

    private final ProjectPlugin projectPlugin;
    // Ensures only a single reconnect loop runs at any given time.
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    // When false, the plugin is shutting down - stop trying to reconnect.
    private volatile boolean shouldReconnect = true;

    public RiftusWebSocket(ProjectPlugin projectPlugin) {
        super(URI.create(getBaseUrl() + "/live/ws"), Map.of(
            "x-live-key", projectPlugin.client().liveKey()
        ));
        this.projectPlugin = projectPlugin;
        // Enables ping/pong so a dead (silently dropped) connection is detected
        // and triggers onClose instead of hanging forever.
        this.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        RiftusLiveDev.instance().getLogger().info("WebSocket is opened for " + projectPlugin.projectInfo().name());
    }

    @Override
    public void onMessage(String message) {
        //System.out.println("Received message: " + message);

        WebSocketData webSocketData = GSON.fromJson(message, WebSocketData.class);
        if (webSocketData.type().equals("artifact_updated")) {
            Bukkit.broadcast(Component.text("Received update for " + this.projectPlugin.projectInfo().name() + "...").color(NamedTextColor.GREEN), "riftuslivedev.broadcastupdate");
            this.projectPlugin.downloadAndLoadPlugin(false);
            Bukkit.broadcast(Component.text("Plugin " + this.projectPlugin.projectInfo().name() + " has been updated!").color(NamedTextColor.GREEN), "riftuslivedev.broadcastupdate");

            // Play sound
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("riftuslivedev.broadcastupdate")) {
                    player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 2.0f));
                }
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        RiftusLiveDev.instance().getLogger().warning("WebSocket closed for " + this.projectPlugin.projectInfo().name() + "! Code: " + code + " Reason: " + reason + " (remote: " + remote + ")");
        if (code == CloseFrame.PROTOCOL_ERROR && reason.contains("401 Unauthorized")) {
            // Live key is invalid, close websocket
            this.shutdown();
            return;
        }
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        RiftusLiveDev.instance().getLogger().log(Level.SEVERE, "WebSocket error for " + this.projectPlugin.projectInfo().name(), ex);
        // onError usually precedes onClose, but when the error happens during the
        // initial connect, onClose is not always delivered - so retry here too.
        scheduleReconnect();
    }

    /**
     * Permanently stops reconnecting and closes the websocket.
     * Called when the plugin is shutting down.
     */
    public void shutdown() {
        this.shouldReconnect = false;
        this.close();
    }

    /**
     * Starts the reconnect loop on a separate thread (if not already running).
     * The reconnecting guard prevents repeated onClose/onError from spawning many threads.
     */
    private void scheduleReconnect() {
        if (!shouldReconnect) {
            return;
        }
        // A loop is already running - do not start another one.
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(this::reconnectLoop, "RiftusWS-Reconnect-" + projectPlugin.projectInfo().name());
        thread.setDaemon(true);
        thread.start();
    }

    private void reconnectLoop() {
        try {
            while (shouldReconnect && !isOpen()) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (!shouldReconnect) {
                    return;
                }

                RiftusLiveDev.instance().getLogger().warning("Reconnecting WebSocket for " + this.projectPlugin.projectInfo().name() + "...");
                try {
                    // reconnectBlocking resets the client state and waits for the connect result.
                    boolean connected = reconnectBlocking();
                    if (connected) {
                        RiftusLiveDev.instance().getLogger().info("WebSocket reconnected for " + this.projectPlugin.projectInfo().name());
                        return;
                    }
                    RiftusLiveDev.instance().getLogger().warning("Reconnect attempt failed for " + this.projectPlugin.projectInfo().name() + ", retrying in " + (RECONNECT_DELAY_MS / 1000) + "s...");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    RiftusLiveDev.instance().getLogger().log(Level.WARNING, "Reconnect attempt errored for " + this.projectPlugin.projectInfo().name() + ", retrying in " + (RECONNECT_DELAY_MS / 1000) + "s...", e);
                }
            }
        } finally {
            reconnecting.set(false);
        }
    }

    public static String getBaseUrl() {
        if (!RiftusLiveDev.instance().pluginConfiguration().dev) {
            return "wss://riftus-api.fanth.pl";
        } else {
            return "ws://localhost:3000";
        }
    }

    public record WebSocketData(String type) {
    }
}