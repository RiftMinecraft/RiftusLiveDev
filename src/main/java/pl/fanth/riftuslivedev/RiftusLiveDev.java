package pl.fanth.riftuslivedev;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import co.aikar.commands.PaperCommandManager;
import core.com.rylinaux.plugman.plugins.PluginManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import pl.fanth.riftuslivedev.api.ApiException;
import pl.fanth.riftuslivedev.api.RiftusAPI;
import pl.fanth.riftuslivedev.commands.RiftusCommand;
import pl.fanth.riftuslivedev.config.ConfigurationFactory;
import pl.fanth.riftuslivedev.config.DataConfiguration;
import pl.fanth.riftuslivedev.config.PluginConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public final class RiftusLiveDev extends JavaPlugin {
    private PluginConfiguration pluginConfiguration;
    private DataConfiguration dataConfiguration;

    private static RiftusLiveDev instance;

    private Plugin dynamicPlugin = null;

    @Override
    public void onEnable() {
        instance = this;

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(new File(this.getDataFolder(), "config.yml"));
        this.dataConfiguration = ConfigurationFactory.createDataConfiguration(new File(this.getDataFolder(), "data.yml"));

        registerCommands();

        this.loadDynamicPlugin();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.unloadDynamicPlugin();
    }

    public void loadDynamicPlugin() {
        try {
            this.getLogger().info("Downloading artifact...");
            RiftusAPI.downloadArtifact();
            this.getLogger().info("Artifact downloaded");
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        PlugManBukkit plugman = (PlugManBukkit) Bukkit.getPluginManager().getPlugin("PlugManX");
        PluginManager plugmanPluginManager = plugman.getPluginManager();

        // Unload
        unloadDynamicPlugin();

        // Load
        try {
            plugmanPluginManager.load(this.getDataFolder().getName() + "/artifacts/artifact");
            dynamicPlugin = Bukkit.getPluginManager().getPlugin(plugman.getFilePluginMap().get("artifact.jar"));
            this.getLogger().info("Loaded plugin: " + dynamicPlugin.getName());
        } catch (Exception e) {
            this.getLogger().severe("Failed to load plugin: " + e.getMessage());
        }
    }

    public void unloadDynamicPlugin() {
        // Unload
        PlugManBukkit plugman = (PlugManBukkit) Bukkit.getPluginManager().getPlugin("PlugManX");
        PluginManager plugmanPluginManager = plugman.getPluginManager();
        if (dynamicPlugin != null) {
            core.com.rylinaux.plugman.plugins.Plugin plugmanPlugin = plugmanPluginManager.getPluginByName(dynamicPlugin.getName());
            plugmanPluginManager.unload(plugmanPlugin);
            this.getLogger().info("Unloaded plugin: " + dynamicPlugin.getName());
            dynamicPlugin = null;
        }
    }

    private void registerCommands() {
        PaperCommandManager manager = new PaperCommandManager(this);

        manager.enableUnstableAPI("help");

        manager.registerCommand(new RiftusCommand());
    }

    public static RiftusLiveDev instance() {
        return instance;
    }

    public PluginConfiguration pluginConfiguration() {
        return pluginConfiguration;
    }

    public DataConfiguration dataConfiguration() {
        return dataConfiguration;
    }

    public void reloadConfiguration() {
        this.pluginConfiguration.load();
        this.dataConfiguration.load();
    }

    public Plugin getDynamicPlugin() {
        return dynamicPlugin;
    }

    public Path getArtifactPath() {
        return this.getDataPath().resolve("artifacts/artifact.jar");
    }
}
