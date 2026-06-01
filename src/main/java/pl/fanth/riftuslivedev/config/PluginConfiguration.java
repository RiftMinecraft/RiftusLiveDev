package pl.fanth.riftuslivedev.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;

@Header("A config file for the plugin.")
@Header("")
public class PluginConfiguration extends OkaeriConfig {
    @Comment("Live key for fetching data from the live server.")
    public String liveKey = "your-live-key-here";

    @Comment("Development mode (DO NOT TOUCH IT, UNLESS YOU KNOW WHAT YOU ARE DOING!).")
    public boolean dev = true;
}
