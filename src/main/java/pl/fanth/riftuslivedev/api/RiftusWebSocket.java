package pl.fanth.riftuslivedev.api;

import com.google.gson.Gson;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.managers.ProjectPlugin;

import java.net.URI;
import java.util.Map;
import java.util.logging.Level;

public class RiftusWebSocket extends WebSocketClient {
    private static final Gson GSON = new Gson();
    private final ProjectPlugin projectPlugin;

    public RiftusWebSocket(ProjectPlugin projectPlugin) {
        super(URI.create(getBaseUrl() + "/live/ws"), Map.of(
            "x-live-key", projectPlugin.liveKey()
        ));
        this.projectPlugin = projectPlugin;
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
            this.projectPlugin.loadPlugin(false);
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
        RiftusLiveDev.instance().getLogger().warning("WebSocket closed for " + this.projectPlugin.projectInfo().name() + "! Code: " + code + " Reason: " + reason);
        if (remote) {
            RiftusLiveDev.instance().getLogger().warning("Reconnecting in 10 seconds...");
            new Thread(() -> {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                RiftusLiveDev.instance().getLogger().warning("Reconnecting now...");
                // Reconnect
                this.reconnect();
            }).start();
        }
    }

    @Override
    public void onError(Exception ex) {
        RiftusLiveDev.instance().getLogger().log(Level.SEVERE, "WebSocket error for " + this.projectPlugin.projectInfo().name(), ex);
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
