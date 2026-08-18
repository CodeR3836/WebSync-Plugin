package com.websync;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class WebSyncCommand implements CommandExecutor {

    private final WebSyncPlugin plugin;

    public WebSyncCommand(WebSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "status" -> {
                WebSyncSettings settings = plugin.settings();
                sender.sendMessage("[WebSync] enabled=" + settings.isEnabled()
                        + " configured=" + settings.isUsable()
                        + " baseUrl=" + settings.baseUrl());
            }
            case "sync" -> {
                if (!plugin.settings().isUsable()) {
                    sender.sendMessage("[WebSync] Not configured — check config.yml.");
                    return true;
                }
                plugin.runFallbackSyncNow();
                sender.sendMessage("[WebSync] Manual sync triggered.");
            }
            case "reload" -> {
                plugin.reloadWebSync();
                sender.sendMessage("[WebSync] Configuration reloaded.");
            }
            default -> sender.sendMessage(usage());
        }
        return true;
    }

    private String usage() {
        return "[WebSync] Usage: /websync <status|sync|reload>";
    }
}
