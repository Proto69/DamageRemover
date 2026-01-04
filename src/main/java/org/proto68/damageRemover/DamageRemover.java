package org.proto68.damageRemover;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.stream.Collectors;

public class DamageRemover extends JavaPlugin implements Listener, org.bukkit.command.TabCompleter {

    private boolean enabled;
    private Set<String> regionNames;
    private Set<EntityDamageEvent.DamageCause> blockedCauses;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("dmgrmvr").setTabCompleter(this);
        getLogger().info("DamageRemover enabled");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();

        enabled = config.getBoolean("enabled", true);

        regionNames = config.getStringList("regionNames")
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        blockedCauses = config.getStringList("damageTypes")
                .stream()
                .map(String::toUpperCase)
                .map(EntityDamageEvent.DamageCause::valueOf)
                .collect(Collectors.toSet());
    }

    // ================= DAMAGE HANDLING =================

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!blockedCauses.contains(event.getCause())) return;

        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));

        if (regionManager == null) return;

        ApplicableRegionSet regions = regionManager.getApplicableRegions(
                BukkitAdapter.asBlockVector(player.getLocation())
        );

        for (ProtectedRegion region : regions) {
            if (regionNames.contains(region.getId().toLowerCase())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ================= COMMANDS =================

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label,
                             String[] args) {

        if (!command.getName().equalsIgnoreCase("dmgrmvr")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage:");
            sender.sendMessage(ChatColor.GRAY + "/dmgrmvr reload");
            sender.sendMessage(ChatColor.GRAY + "/dmgrmvr info");
            return true;
        }

        // ================= RELOAD =================
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadConfigValues();
            sender.sendMessage(ChatColor.GREEN + "DamageRemover config reloaded.");
            return true;
        }

        // ================= INFO =================
        if (args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            var regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(player.getWorld()));

            if (regionManager == null) {
                player.sendMessage(ChatColor.RED + "No region data available.");
                return true;
            }

            var regions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(player.getLocation())
            );

            player.sendMessage(ChatColor.YELLOW + "=== DamageRemover Info ===");

            if (regions.size() == 0) {
                player.sendMessage(ChatColor.GRAY + "No regions at your location.");
                return true;
            }

            for (ProtectedRegion region : regions) {
                String regionId = region.getId().toLowerCase();
                boolean managed = regionNames.contains(regionId);

                player.sendMessage(ChatColor.AQUA + "Region: " + regionId);
                player.sendMessage(ChatColor.GRAY + "Managed: "
                        + (managed ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

                if (managed) {
                    player.sendMessage(ChatColor.GRAY + "Blocked damage:");
                    for (var cause : blockedCauses) {
                        player.sendMessage(ChatColor.RED + " - " + cause.name().toLowerCase());
                    }
                }
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }

    // ================= TAB COMPLETER =================

    @Override
    public java.util.List<String> onTabComplete(
            org.bukkit.command.CommandSender sender,
            org.bukkit.command.Command command,
            String alias,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("dmgrmvr")) return null;

        if (args.length == 1) {
            return java.util.List.of("reload", "info")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return java.util.Collections.emptyList();
    }


}
