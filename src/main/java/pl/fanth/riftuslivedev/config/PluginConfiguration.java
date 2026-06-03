package pl.fanth.riftuslivedev.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;

import java.util.HashSet;
import java.util.Set;

@Header("A config file for the plugin.")
@Header("")
public class PluginConfiguration extends OkaeriConfig {
    @Comment("Live key for fetching data from the live server.")
    public Set<String> liveKeys = new HashSet<>();

    @Comment("Development mode (DO NOT TOUCH IT, UNLESS YOU KNOW WHAT YOU ARE DOING!).")
    public boolean dev = false;
}
