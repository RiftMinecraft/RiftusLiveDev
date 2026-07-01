package pl.fanth.riftuslivedev;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import pl.fanth.riftuslivedev.commands.RiftusCommand;
import pl.fanth.riftuslivedev.config.ConfigurationFactory;
import pl.fanth.riftuslivedev.config.PluginConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.fanth.riftuslivedev.projects.ProjectManager;
import pl.fanth.riftuslivedev.projects.ProjectPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class RiftusLiveDev extends JavaPlugin {
    private PluginConfiguration pluginConfiguration;

    private static RiftusLiveDev instance;

    @Override
    public void onLoad() {
        instance = this;

        this.pluginConfiguration = ConfigurationFactory.createConfiguration(PluginConfiguration.class, new File(this.getDataFolder(), "config.yml"));

        this.getLogger().info("Loading plugins...");
        Set<String> toRemove = new HashSet<>();
        for (String liveKey : this.pluginConfiguration.liveKeys) {
            try {
                ProjectPlugin projectPlugin = ProjectManager.addLiveKeyAndLoad(liveKey, true);
                if (projectPlugin == null) {
                    this.getLogger().warning("Invalid live key. Removing from config...");
                    toRemove.add(liveKey);
                }
            } catch (Exception e) {
                this.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load plugin for live key: " + liveKey, e);
            }
        }

        this.pluginConfiguration.liveKeys.removeAll(toRemove);
    }

    @Override
    public void onEnable() {
        registerCommands();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (ProjectPlugin projectPlugin : ProjectManager.getProjectPlugins()) {
            projectPlugin.shutdown();
        }
    }

    private void registerCommands() {
        PaperCommandManager manager = new PaperCommandManager(this);

        manager.enableUnstableAPI("help");

        manager.getCommandContexts().registerContext(ProjectPlugin.class, ctx -> {
            String projectName = ctx.popFirstArg();
            ProjectPlugin projectPlugin = ProjectManager.getByName(projectName);
            if (projectPlugin == null) {
                throw new InvalidCommandArgument("Project not found");
            }
            return projectPlugin;
        });

        manager.getCommandCompletions().registerCompletion("projects", c -> {
            return ProjectManager.getProjectNames();
        });

        manager.registerCommand(new RiftusCommand());
    }

    public static RiftusLiveDev instance() {
        return instance;
    }

    public PluginConfiguration pluginConfiguration() {
        return pluginConfiguration;
    }

    public void reloadConfiguration() {
        this.pluginConfiguration.load();
    }
}
