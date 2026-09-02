package net.tfminecraft.woodworking.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.woodworking.cache.Cache;

public final class Permissions {

    public static final String ADMIN = "woodworking.admin";

    private Permissions() {
    }

    public static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(ADMIN);
    }

    public static boolean canUse(CommandSender sender) {
        if (isAdmin(sender)) return true;
        String perm = Cache.permission;
        if (perm == null || perm.isBlank()) return true;
        return sender.hasPermission(perm);
    }

    public static boolean requireAdmin(CommandSender sender) {
        if (isAdmin(sender)) return true;
        sender.sendMessage("§cYou do not have access to this command!");
        return false;
    }

    public static boolean requireUse(Player player) {
        if (canUse(player)) return true;
        player.sendMessage("§cYou do not have permission to use woodworking.");
        return false;
    }
}
