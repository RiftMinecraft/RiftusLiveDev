package pl.fanth.riftuslivedev.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.fanth.riftuslivedev.RiftusLiveDev;
import pl.fanth.riftuslivedev.managers.ProjectManager;
import pl.fanth.riftuslivedev.managers.ProjectPlugin;

import java.util.Collection;

@CommandAlias("riftus")
@CommandPermission("riftus.admin")
public class RiftusCommand extends BaseCommand {
    @HelpCommand
    public void doHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("addplugin")
    @Description("Adds a new plugin from a livekey")
    @CommandPermission("riftus.admin.addplugin")
    public void addPlugin(CommandSender sender, String liveKey) {
        ProjectPlugin projectPlugin = ProjectManager.addLiveKeyAndLoad(liveKey, false);
        RiftusLiveDev.instance().pluginConfiguration().liveKeys.add(liveKey);
        RiftusLiveDev.instance().pluginConfiguration().save();

        sender.sendMessage(Component.text("Loaded new plugin '" + projectPlugin.projectInfo().name() + "'").color(NamedTextColor.GREEN));
    }

    @Subcommand("update")
    @Description("Updates the plugin by downloading the latest artifact and reloading it.")
    @CommandPermission("riftus.admin.update")
    @CommandCompletion("@projects")
    public void updateArtifact(CommandSender sender, ProjectPlugin projectPlugin) {
        sender.sendMessage(Component.text("Updating plugin...").color(NamedTextColor.GREEN));
        projectPlugin.downloadAndLoadPlugin(false);
        sender.sendMessage(Component.text("Updated plugin!").color(NamedTextColor.GREEN));
        if (sender instanceof Player player) {
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 2.0f));
        }
    }

    @Subcommand("list")
    @Description("Lists all loaded plugins.")
    @CommandPermission("riftus.admin.list")
    public void listProjects(CommandSender sender) {
        Collection<ProjectPlugin> projects = ProjectManager.getProjectPlugins();

        if (projects.isEmpty()) {
            sender.sendMessage(Component.text("Brak załadowanych pluginów.").color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Załadowane pluginy (" + projects.size() + "):").color(NamedTextColor.GOLD));

        for (ProjectPlugin projectPlugin : projects) {
            boolean loaded = projectPlugin.plugin() != null;
            Component status = loaded
                ? Component.text("● ").color(NamedTextColor.GREEN)
                : Component.text("● ").color(NamedTextColor.RED);

            sender.sendMessage(status
                .append(Component.text(projectPlugin.projectInfo().name()).color(NamedTextColor.AQUA))
                .append(Component.text(loaded ? " (załadowany)" : " (niezaładowany)").color(NamedTextColor.GRAY)));
        }
    }

    @Subcommand("removeplugin")
    @Description("Removes loaded plugin")
    @CommandPermission("riftus.admin.removeplugin")
    @CommandCompletion("@projects")
    public void removePlugin(CommandSender sender, ProjectPlugin projectPlugin) {
        sender.sendMessage(Component.text("Unloading plugin...").color(NamedTextColor.GREEN));

        projectPlugin.unloadPlugin();
        ProjectManager.removeProjectPlugin(projectPlugin.projectInfo().name());

        RiftusLiveDev.instance().pluginConfiguration().liveKeys.remove(projectPlugin.liveKey());
        RiftusLiveDev.instance().pluginConfiguration().save();

        sender.sendMessage(Component.text("Unloaded plugin '" + projectPlugin.projectInfo().name() + "'").color(NamedTextColor.GREEN));
    }
}
