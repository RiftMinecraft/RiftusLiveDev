package pl.fanth.riftuslivedev.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
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

@CommandAlias("riftus")
@CommandPermission("riftus.admin")
public class RiftusCommand extends BaseCommand {
    @HelpCommand
    public void doHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("update")
    @Description("Updates the plugin by downloading the latest artifact and reloading it.")
    public void updateArtifact(CommandSender sender) {
        sender.sendMessage(Component.text("Updating plugin...").color(NamedTextColor.GREEN));
        RiftusLiveDev.instance().loadDynamicPlugin();
        sender.sendMessage(Component.text("Updated plugin!").color(NamedTextColor.GREEN));
        if (sender instanceof Player player) {
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.MASTER, 1.0f, 2.0f));
        }
    }
}
