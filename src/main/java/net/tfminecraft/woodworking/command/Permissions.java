package net.tfminecraft.woodworking.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.woodworking.cache.Cache;

public final class Permissions {

    public static final String ADMIN = "woodworking.admin";

    /** Shown when a player without the woodworking profession tries to use a bench. */
    public static final String NOT_SKILLED = "§cYou are not skilled enough to work wood.";

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
        player.sendMessage(NOT_SKILLED);
        return false;
    }
}
