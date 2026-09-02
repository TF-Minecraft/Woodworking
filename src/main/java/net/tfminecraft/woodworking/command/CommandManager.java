package net.tfminecraft.woodworking.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.woodworking.Woodworking;
import net.tfminecraft.woodworking.loader.ProjectLoader;
import net.tfminecraft.woodworking.project.WoodProject;
import net.tfminecraft.woodworking.station.StationManager;
import net.tfminecraft.woodworking.station.WoodStation;

public class CommandManager implements CommandExecutor, TabCompleter {

    public String cmd1 = "woodworking";

    private static final List<String> SUB_COMMANDS = Arrays.asList("reload", "select");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase(cmd1)) return false;

        if (args.length == 0) {
            sender.sendMessage("§e/woodworking reload");
            sender.sendMessage("§e/woodworking select <projectId>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!Permissions.requireAdmin(sender)) return true;

            sender.sendMessage("§a[Woodworking]§e Reloading plugin...");
            Woodworking.plugin.reload();
            sender.sendMessage("§a[Woodworking]§e Reload complete.");
            sender.sendMessage("§7" + Woodworking.plugin.loadSummary());
            return true;
        }

        if (args[0].equalsIgnoreCase("select")) {
            if (!Permissions.requireAdmin(sender)) return true;
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can select a project on a bench.");
                return true;
            }
            if (!Permissions.requireUse(p)) return true;
            if (args.length < 2) {
                p.sendMessage("§cUsage: /woodworking select <projectId>");
                return true;
            }
            handleSelect(p, args[1]);
            return true;
        }

        sender.sendMessage("§e/woodworking reload");
        sender.sendMessage("§e/woodworking select <projectId>");
        return true;
    }

    private void handleSelect(Player p, String projectId) {
        WoodProject project = ProjectLoader.getByString(projectId);
        if (project == null) {
            p.sendMessage("§cUnknown project '" + projectId + "'.");
            return;
        }

        Block target = p.getTargetBlockExact(6);
        StationManager stations = Woodworking.plugin.getStations();
        if (target == null || !stations.isWoodworkingStation(target)) {
            p.sendMessage("§cLook at a woodworking bench within 6 blocks.");
            return;
        }

        WoodStation station = stations.getOrCreate(target.getLocation());
        if (station.hasProject()) {
            p.sendMessage("§cThis bench already has a project. SHIFT + LEFT CLICK with the branding tool to cancel first.");
            return;
        }

        station.setProject(project);
        stations.markDirty();
        p.sendMessage("§aSelected " + project.getName() + " §aas the current woodworking project");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.8f, 2f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase(cmd1)) return null;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(prefix)) out.add(s);
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            if (!Permissions.isAdmin(sender)) return new ArrayList<>();
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : ProjectLoader.get().keySet()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(id);
                if (out.size() >= 40) break;
            }
            return out;
        }

        return new ArrayList<>();
    }
}
