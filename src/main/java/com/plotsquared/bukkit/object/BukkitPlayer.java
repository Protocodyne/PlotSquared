package com.plotsquared.bukkit.object;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.util.EconHandler;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.PlotGamemode;
import com.intellectualcrafters.plot.util.PlotWeather;
import com.intellectualcrafters.plot.util.UUIDHandler;
import com.plotsquared.bukkit.util.BukkitUtil;

public class BukkitPlayer extends PlotPlayer {
    
    public final Player player;
    private UUID uuid;
    private String name;
    private long last = 0;
    public boolean offline;
    
    /**
     * Please do not use this method. Instead use BukkitUtil.getPlayer(Player), as it caches player objects.
     * @param player
     */
    public BukkitPlayer(final Player player) {
        this.player = player;
    }
    
    public BukkitPlayer(final Player player, final boolean offline) {
        this.player = player;
        this.offline = offline;
    }
    
    @Override
    public long getPreviousLogin() {
        if (last == 0) {
            last = player.getLastPlayed();
        }
        return last;
    }
    
    @Override
    public Location getLocation() {
        final Location loc = super.getLocation();
        return loc == null ? BukkitUtil.getLocation(player) : loc;
    }
    
    @Override
    public UUID getUUID() {
        if (uuid == null) {
            uuid = UUIDHandler.getUUID(this);
        }
        return uuid;
    }
    
    @Override
    public boolean hasPermission(final String node) {
        if (offline && (EconHandler.manager != null)) {
            return EconHandler.manager.hasPermission(getName(), node);
        }
        return player.hasPermission(node);
    }
    
    public Permission getPermission(final String node) {
        final PluginManager manager = Bukkit.getPluginManager();
        Permission perm = manager.getPermission(node);
        if (perm == null) {
            final String[] nodes = node.split("\\.");
            perm = new Permission(node);
            final StringBuilder n = new StringBuilder();
            for (int i = 0; i < (nodes.length - 1); i++) {
                n.append(nodes[i] + ("."));
                if (!node.equals(n + C.PERMISSION_STAR.s())) {
                    final Permission parent = getPermission(n + C.PERMISSION_STAR.s());
                    if (parent != null) {
                        perm.addParent(parent, true);
                    }
                }
            }
            manager.addPermission(perm);
        }
        manager.recalculatePermissionDefaults(perm);
        perm.recalculatePermissibles();
        return perm;
    }
    
    @Override
    public void sendMessage(final String message) {
        player.sendMessage(message);
    }
    
    @Override
    public void sendMessage(final C c, final String... args) {
        MainUtil.sendMessage(this, c, args);
    }
    
    @Override
    public void teleport(final Location loc) {
        if ((Math.abs(loc.getX()) >= 30000000) || (Math.abs(loc.getZ()) >= 30000000)) {
            return;
        }
        player.teleport(new org.bukkit.Location(BukkitUtil.getWorld(loc.getWorld()), loc.getX() + 0.5, loc.getY(), loc.getZ() + 0.5, loc.getYaw(), loc.getPitch()), TeleportCause.COMMAND);
    }
    
    @Override
    public String getName() {
        if (name == null) {
            name = player.getName();
        }
        return name;
    }
    
    @Override
    public boolean isOnline() {
        return !offline && player.isOnline();
    }
    
    @Override
    public void setCompassTarget(final Location loc) {
        player.setCompassTarget(new org.bukkit.Location(BukkitUtil.getWorld(loc.getWorld()), loc.getX(), loc.getY(), loc.getZ()));
        
    }
    
    @Override
    public Location getLocationFull() {
        return BukkitUtil.getLocationFull(player);
    }
    
    @Override
    public void setAttribute(String key) {
        key = "plotsquared_user_attributes." + key;
        if ((EconHandler.manager == null) || player.hasPermission("plotsquared_user_attributes.*")) {
            setMeta(key, true);
            return;
        }
        EconHandler.manager.setPermission(getName(), key, true);
    }
    
    @Override
    public boolean getAttribute(String key) {
        key = "plotsquared_user_attributes." + key;
        if ((EconHandler.manager == null) || player.hasPermission("plotsquared_user_attributes.*")) {
            final Object v = getMeta(key);
            return v == null ? false : (Boolean) v;
        }
        Permission perm = Bukkit.getServer().getPluginManager().getPermission(key);
        if (perm == null) {
            try {
                perm = new Permission(key, PermissionDefault.FALSE);
                Bukkit.getServer().getPluginManager().addPermission(perm);
                Bukkit.getServer().getPluginManager().recalculatePermissionDefaults(perm);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return player.hasPermission(key);
    }
    
    @Override
    public void removeAttribute(String key) {
        key = "plotsquared_user_attributes." + key;
        if ((EconHandler.manager == null) || player.hasPermission("plotsquared_user_attributes.*")) {
            deleteMeta(key);
            return;
        }
        EconHandler.manager.setPermission(getName(), key, false);
    }
    
    @Override
    public void loadData() {
        if (!player.isOnline()) {
            player.loadData();
        }
    }
    
    @Override
    public void saveData() {
        player.saveData();
    }
    
    @Override
    public void setWeather(final PlotWeather weather) {
        switch (weather) {
            case CLEAR:
                player.setPlayerWeather(WeatherType.CLEAR);
                return;
            case RAIN: {
                player.setPlayerWeather(WeatherType.DOWNFALL);
                return;
            }
            case RESET:
                player.resetPlayerWeather();
                return;
        }
    }
    
    @Override
    public PlotGamemode getGamemode() {
        switch (player.getGameMode()) {
            case ADVENTURE:
                return PlotGamemode.ADVENTURE;
            case CREATIVE:
                return PlotGamemode.CREATIVE;
            case SPECTATOR:
                return PlotGamemode.SPECTATOR;
            case SURVIVAL:
                return PlotGamemode.SURVIVAL;
        }
        return null;
    }
    
    @Override
    public void setGamemode(final PlotGamemode gamemode) {
        switch (gamemode) {
            case ADVENTURE:
                player.setGameMode(GameMode.ADVENTURE);
                return;
            case CREATIVE:
                player.setGameMode(GameMode.CREATIVE);
                return;
            case SPECTATOR:
                player.setGameMode(GameMode.SPECTATOR);
                return;
            case SURVIVAL:
                player.setGameMode(GameMode.SURVIVAL);
                return;
        }
    }
    
    @Override
    public void setTime(final long time) {
        if (time != Long.MAX_VALUE) {
            player.setPlayerTime(time, false);
        } else {
            player.resetPlayerTime();
        }
    }
    
    @Override
    public void setFlight(final boolean fly) {
        player.setAllowFlight(fly);
    }
    
    @Override
    public void playMusic(final Location loc, final int id) {
        player.playEffect(BukkitUtil.getLocation(loc), Effect.RECORD_PLAY, id);
    }
    
    @Override
    public void kick(final String message) {
        player.kickPlayer(message);
    }
    
    @Override
    public boolean isBanned() {
        return player.isBanned();
    }
}
