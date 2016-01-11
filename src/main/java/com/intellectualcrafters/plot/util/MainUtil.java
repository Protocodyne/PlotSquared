////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////
package com.intellectualcrafters.plot.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import com.google.common.collect.BiMap;
import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.config.Settings;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.flag.Flag;
import com.intellectualcrafters.plot.flag.FlagManager;
import com.intellectualcrafters.plot.object.BlockLoc;
import com.intellectualcrafters.plot.object.ChunkLoc;
import com.intellectualcrafters.plot.object.ConsolePlayer;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotCluster;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotManager;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.PlotSettings;
import com.intellectualcrafters.plot.object.PlotWorld;
import com.intellectualcrafters.plot.object.PseudoRandom;
import com.intellectualcrafters.plot.object.RegionWrapper;
import com.intellectualcrafters.plot.object.RunnableVal;
import com.intellectualcrafters.plot.object.StringWrapper;
import com.plotsquared.listener.PlotListener;

/**
 * plot functions
 *
 */
public class MainUtil {
    /**
     * If the NMS code for sending chunk updates is functional<br>
     *  - E.g. If using an older version of Bukkit, or before the plugin is updated to 1.5<br>
     *  - Slower fallback code will be used if not.<br>
     */
    public static boolean canSendChunk = false;
    
    /**
     * Cache for last auto claimed plot.<br>
     *  - Used for efficiently calculating the next claimable plot<br>
     */
    public static HashMap<String, PlotId> lastPlot = new HashMap<>();
    
    /**
     * Cache of the furthest claimed plot<br>
     *  - Used for efficiently calculating the plot border distance
     */
    public static HashMap<String, Integer> worldBorder = new HashMap<>();
    
    /**
     * Pseudorandom object<br>
     *  - Not truly random, but good enough for a game<br>
     *  - A lot more efficient than Random<br>
     */
    public static PseudoRandom random = new PseudoRandom();
    
    /**
     * Cache of mapping x,y,z coordinates to the chunk array<br>
     *  - Used for efficent world generation<br>
     */
    public static short[][] x_loc;
    public static short[][] y_loc;
    public static short[][] z_loc;
    
    /**
     * This cache is used for world generation and just saves a bit of calculation time when checking if something is in the plot area.
     */
    public static void initCache() {
        if (x_loc == null) {
            x_loc = new short[16][4096];
            y_loc = new short[16][4096];
            z_loc = new short[16][4096];
            for (int i = 0; i < 16; i++) {
                final int i4 = i << 4;
                for (int j = 0; j < 4096; j++) {
                    final int y = (i4) + (j >> 8);
                    final int a = (j - ((y & 0xF) << 8));
                    final int z1 = (a >> 4);
                    final int x1 = a - (z1 << 4);
                    x_loc[i][j] = (short) x1;
                    y_loc[i][j] = (short) y;
                    z_loc[i][j] = (short) z1;
                }
            }
        }
    }
    
    /**
     * Attempt to find the largest rectangular region in a plot (as plots can form non rectangular shapes) 
     * @param plot
     * @return
     */
    public static RegionWrapper getLargestRegion(Plot plot) {
        HashSet<RegionWrapper> regions = getRegions(plot);
        RegionWrapper max = null;
        int area = 0;
        for (RegionWrapper region : regions) {
            int current = (region.maxX - region.minX + 1) * (region.maxZ - region.minZ + 1);
            if (current > area) {
                max = region;
                area = current;
            }
        }
        return max;
    }
    
    /**
     * This will combine each plot into effective rectangular regions
     * @param origin
     * @return
     */
    public static HashSet<RegionWrapper> getRegions(Plot origin) {
        if (regions_cache != null && connected_cache != null && connected_cache.contains(origin)) {
            return regions_cache;
        }
        if (!origin.isMerged()) {
            final Location pos1 = MainUtil.getPlotBottomLocAbs(origin.world, origin.getId());
            final Location pos2 = MainUtil.getPlotTopLocAbs(origin.world, origin.getId());
            connected_cache = new HashSet<>(Collections.singletonList(origin));
            regions_cache = new HashSet<>(1);
            regions_cache.add(new RegionWrapper(pos1.getX(), pos2.getX(), pos1.getY(), pos2.getY(), pos1.getZ(), pos2.getZ()));
            return regions_cache;
        }
        HashSet<Plot> plots = getConnectedPlots(origin);
        regions_cache = new HashSet<>();
        HashSet<PlotId> visited = new HashSet<>();
        ArrayList<PlotId> ids;
        for (Plot current : plots) {
            if (visited.contains(current.getId())) {
                continue;
            }
            boolean merge = true;
            boolean tmp = true;
            PlotId bot = new PlotId(current.getId().x, current.getId().y);
            PlotId top = new PlotId(current.getId().x, current.getId().y);
            while (merge) {
                merge = false;
                ids = getPlotSelectionIds(new PlotId(bot.x, bot.y - 1), new PlotId(top.x, bot.y - 1));
                tmp = true;
                for (PlotId id : ids) {
                    Plot plot = MainUtil.getPlotAbs(origin.world, id);
                    if (plot == null || !plot.getMerged(2) || (visited.contains(plot.getId()))) {
                        tmp = false;
                    }
                }
                if (tmp) {
                    merge = true;
                    bot.y--;
                }
                ids = getPlotSelectionIds(new PlotId(top.x + 1, bot.y), new PlotId(top.x + 1, top.y));
                tmp = true;
                for (PlotId id : ids) {
                    Plot plot = MainUtil.getPlotAbs(origin.world, id);
                    if (plot == null || !plot.getMerged(3) || (visited.contains(plot.getId()))) {
                        tmp = false;
                    }
                }
                if (tmp) {
                    merge = true;
                    top.x++;
                }
                ids = getPlotSelectionIds(new PlotId(bot.x, top.y + 1), new PlotId(top.x, top.y + 1));
                tmp = true;
                for (PlotId id : ids) {
                    Plot plot = MainUtil.getPlotAbs(origin.world, id);
                    if (plot == null || !plot.getMerged(0) || (visited.contains(plot.getId()))) {
                        tmp = false;
                    }
                }
                if (tmp) {
                    merge = true;
                    top.y++;
                }
                ids = getPlotSelectionIds(new PlotId(bot.x - 1, bot.y), new PlotId(bot.x - 1, top.y));
                tmp = true;
                for (PlotId id : ids) {
                    Plot plot = MainUtil.getPlotAbs(origin.world, id);
                    if (plot == null || !plot.getMerged(1) || (visited.contains(plot.getId()))) {
                        tmp = false;
                    }
                }
                if (tmp) {
                    merge = true;
                    bot.x--;
                }
            }
            Location gtopabs = getPlotAbs(origin.world, top).getTopAbs();
            Location gbotabs = getPlotAbs(origin.world, bot).getBottomAbs();
            for (PlotId id : getPlotSelectionIds(bot, top)) {
                visited.add(id);
            }
            for (int x = bot.x; x <= top.x; x++) {
                Plot plot = getPlotAbs(current.world, new PlotId(x, top.y));
                if (plot.getMerged(2)) {
                    // south wedge
                    Location toploc = getPlotTopLoc_(plot);
                    Location botabs = plot.getBottomAbs();
                    Location topabs = plot.getTopAbs();
                    regions_cache.add(new RegionWrapper(botabs.getX(), topabs.getX(), topabs.getZ() + 1, toploc.getZ()));
                    if (plot.getMerged(5)) {
                        regions_cache.add(new RegionWrapper(topabs.getX() + 1, toploc.getX(), topabs.getZ() + 1, toploc.getZ()));
                        // intersection
                    }
                }
            }
            
            for (int y = bot.y; y <= top.y; y++) {
                Plot plot = getPlotAbs(current.world, new PlotId(top.x, y));
                if (plot.getMerged(1)) {
                    // east wedge
                    Location toploc = getPlotTopLoc_(plot);
                    Location botabs = plot.getBottomAbs();
                    Location topabs = plot.getTopAbs();
                    regions_cache.add(new RegionWrapper(topabs.getX() + 1, toploc.getX(), botabs.getZ(), topabs.getZ()));
                    if (plot.getMerged(5)) {
                        regions_cache.add(new RegionWrapper(topabs.getX() + 1, toploc.getX(), topabs.getZ() + 1, toploc.getZ()));
                        // intersection
                    }
                }
            }
            regions_cache.add(new RegionWrapper(gbotabs.getX(), gtopabs.getX(), gbotabs.getZ(), gtopabs.getZ()));
        }
        return regions_cache;
    }
    
    /**
     * Hashcode of a boolean array.<br>
     *  - Used for traversing mega plots quickly.  
     * @param array
     * @return hashcode
     */
    public static int hash(boolean[] array) {
        if (array.length == 4) {
            if (!array[0] && !array[1] && !array[2] && !array[3]) {
                return 0;
            }
            return ((array[0] ? 1 : 0) << 3) + ((array[1] ? 1 : 0) << 2) + ((array[2] ? 1 : 0) << 1) + (array[3] ? 1 : 0);
        }
        int n = 0;
        for (int j = 0; j < array.length; ++j) {
            n = (n << 1) + (array[j] ? 1 : 0);
        }
        return n;
    }
    
    /**
     * Check if a location is in a plot area (roads / plots).<br>
     *  - A world that is a plot world may not be a plot area if clusters are used<br>
     *  - A non plot world is not a plot area<br>
     * @param location
     * @return
     */
    public static boolean isPlotArea(final Location location) {
        final PlotWorld plotworld = PS.get().getPlotWorld(location.getWorld());
        if (plotworld == null) {
            return false;
        }
        if (plotworld.TYPE == 2) {
            return ClusterManager.getCluster(location) != null;
        }
        return true;
    }
    
    /**
     * Get the name from a UUID<br>
     * @param owner
     * @return The player's name, None, Everyone or Unknown 
     */
    public static String getName(final UUID owner) {
        if (owner == null) {
            return C.NONE.s();
        } else if (owner.equals(DBFunc.everyone)) {
            return C.EVERYONE.s();
        }
        final String name = UUIDHandler.getName(owner);
        if (name == null) {
            return C.UNKNOWN.s();
        }
        return name;
    }
    
    /**
     * Efficiently get a list of PlotPlayers inside a plot<br>
     *  - PlotSquared caches player locations
     * @param plot
     * @return
     */
    public static List<PlotPlayer> getPlayersInPlot(final Plot plot) {
        final ArrayList<PlotPlayer> players = new ArrayList<>();
        for (Entry<String, PlotPlayer> entry : UUIDHandler.getPlayers().entrySet()) {
            PlotPlayer pp = entry.getValue();
            if (plot.equals(pp.getCurrentPlot())) {
                players.add(pp);
            }
        }
        return players;
    }
    
    /**
     * Retrigger plot entry functions for the players in a plot.<br>
     *  - Used when plot settings are changed<br>
     * @param plot
     */
    public static void reEnterPlot(final Plot plot) {
        TaskManager.runTaskLater(new Runnable() {
            @Override
            public void run() {
                for (final PlotPlayer pp : getPlayersInPlot(plot)) {
                    PlotListener.plotExit(pp, plot);
                    PlotListener.plotEntry(pp, plot);
                }
            }
        }, 1);
    }
    
    /**
     * Break up a series of tasks so that they can run without lagging the server
     * @param objects
     * @param task
     * @param whenDone
     */
    public static <T> void objectTask(Collection<T> objects, final RunnableVal<T> task, final Runnable whenDone) {
        final Iterator<T> iter = objects.iterator();
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                boolean hasNext;
                while ((hasNext = iter.hasNext()) && System.currentTimeMillis() - start < 5) {
                    task.value = iter.next();
                    task.run();
                }
                if (!hasNext) {
                    TaskManager.runTaskLater(whenDone, 1);
                } else {
                    TaskManager.runTaskLater(this, 1);
                }
            }
        });
    }
    
    /**
     * Fuzzy plot search with spaces separating terms<br>
     *  - Terms: id, alias, world, owner, trusted, member
     * @param search
     * @return
     */
    public static List<Plot> getPlotsBySearch(final String search) {
        final String[] split = search.split(" ");
        final int size = split.length * 2;
        
        final List<UUID> uuids = new ArrayList<>();
        PlotId id = null;
        String world = null;
        String alias = null;
        
        for (final String term : split) {
            try {
                UUID uuid = UUIDHandler.getUUID(term, null);
                if (uuid == null) {
                    uuid = UUID.fromString(term);
                }
                if (uuid != null) {
                    uuids.add(uuid);
                    continue;
                }
            } catch (final Exception e) {
                id = PlotId.fromString(term);
                if (id != null) {
                    continue;
                }
                for (final String pw : PS.get().getPlotWorlds()) {
                    if (pw.equalsIgnoreCase(term)) {
                        world = pw;
                        break;
                    }
                }
                if (world == null) {
                    alias = term;
                }
            }
        }
        
        final ArrayList<ArrayList<Plot>> plotList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            plotList.add(new ArrayList<Plot>());
        }
        
        for (final Plot plot : PS.get().getPlots()) {
            int count = 0;
            if (uuids.size() > 0) {
                for (final UUID uuid : uuids) {
                    if (plot.isOwner(uuid)) {
                        count += 2;
                    } else if (plot.isAdded(uuid)) {
                        count++;
                    }
                }
            }
            if (id != null) {
                if (plot.getId().equals(id)) {
                    count++;
                }
            }
            if ((world != null) && plot.world.equals(world)) {
                count++;
            }
            if ((alias != null) && alias.equals(plot.getAlias())) {
                count += 2;
            }
            if (count != 0) {
                plotList.get(count - 1).add(plot);
            }
        }
        
        final List<Plot> plots = new ArrayList<Plot>();
        for (int i = plotList.size() - 1; i >= 0; i--) {
            if (plotList.get(i).size() > 0) {
                plots.addAll(plotList.get(i));
            }
        }
        return plots;
    }
    
    /**
     * Get the plot from a string<br>
     * @param player Provides a context for what world to search in. Prefixing the term with 'world_name;' will override this context.  
     * @param arg The search term
     * @param message If a message should be sent to the player if a plot cannot be found
     * @return The plot if only 1 result is found, or null
     */
    public static Plot getPlotFromString(final PlotPlayer player, final String arg, final boolean message) {
        if (arg == null) {
            if (player == null) {
                if (message) {
                    MainUtil.sendMessage(player, C.NOT_VALID_PLOT_WORLD);
                }
                return null;
            }
            return getPlotAbs(player.getLocation());
        }
        String worldname = null;
        PlotId id = null;
        if (player != null) {
            worldname = player.getLocation().getWorld();
        }
        final String[] split = arg.split(";|,");
        if (split.length == 3) {
            worldname = split[0];
            id = PlotId.fromString(split[1] + ";" + split[2]);
        } else if (split.length == 2) {
            id = PlotId.fromString(arg);
        } else {
            if (worldname == null) {
                if (PS.get().getPlotWorlds().size() == 0) {
                    if (message) {
                        MainUtil.sendMessage(player, C.NOT_VALID_PLOT_WORLD);
                    }
                    return null;
                }
                worldname = PS.get().getPlotWorlds().iterator().next();
            }
            for (final Plot p : PS.get().getPlotsInWorld(worldname)) {
                final String name = p.getAlias();
                if ((name.length() != 0) && StringMan.isEqualIgnoreCase(name, arg)) {
                    return p;
                }
            }
            for (final String world : PS.get().getPlotWorlds()) {
                if (!world.endsWith(worldname)) {
                    for (final Plot p : PS.get().getPlotsInWorld(world)) {
                        final String name = p.getAlias();
                        if ((name.length() != 0) && name.equalsIgnoreCase(arg)) {
                            return p;
                        }
                    }
                }
            }
        }
        if ((worldname == null) || !PS.get().isPlotWorld(worldname)) {
            if (message) {
                MainUtil.sendMessage(player, C.NOT_VALID_PLOT_WORLD);
            }
            return null;
        }
        if (id == null) {
            if (message) {
                MainUtil.sendMessage(player, C.NOT_VALID_PLOT_ID);
            }
            return null;
        }
        return getPlotAbs(worldname, id);
    }
    
    /**
     * Merges all plots in the arraylist (with cost)
     *
     * @param world
     * @param plotIds
     *
     * @return boolean
     */
    public static boolean mergePlots(final PlotPlayer player, final String world, final ArrayList<PlotId> plotIds) {
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        if ((EconHandler.manager != null) && plotworld.USE_ECONOMY) {
            final double cost = plotIds.size() * plotworld.MERGE_PRICE;
            if (cost > 0d) {
                if (EconHandler.manager.getMoney(player) < cost) {
                    MainUtil.sendMessage(player, C.CANNOT_AFFORD_MERGE, "" + cost);
                    return false;
                }
                EconHandler.manager.withdrawMoney(player, cost);
                MainUtil.sendMessage(player, C.REMOVED_BALANCE, cost + "");
            }
        }
        return MainUtil.mergePlots(world, plotIds, true, true);
    }
    
    /**
     * Unlink the plot and all connected plots
     * @param plot
     * @param createRoad
     * @return
     */
    public static boolean unlinkPlot(final Plot plot, final boolean createRoad, boolean createSign) {
        if (!plot.isMerged()) {
            return false;
        }
        HashSet<Plot> plots = getConnectedPlots(plot);
        ArrayList<PlotId> ids = new ArrayList<>(plots.size());
        for (Plot current : plots) {
            current.setHome(null);
            ids.add(current.getId());
        }
        final boolean result = EventUtil.manager.callUnlink(plot.world, ids);
        if (!result) {
            return false;
        }
        plot.clearRatings();
        if (createSign) {
            plot.removeSign();
        }
        final PlotManager manager = plot.getManager();
        final PlotWorld plotworld = plot.getWorld();
        if (createRoad) {
            manager.startPlotUnlink(plotworld, ids);
        }
        if ((plotworld.TERRAIN != 3) && createRoad) {
            for (Plot current : plots) {
                if (current.getMerged(1)) {
                    manager.createRoadEast(plotworld, current);
                    if (current.getMerged(2)) {
                        manager.createRoadSouth(plotworld, current);
                        if (current.getMerged(5)) {
                            manager.createRoadSouthEast(plotworld, current);
                        }
                    }
                }
                else if (current.getMerged(2)) {
                    manager.createRoadSouth(plotworld, current);
                }
            }
        }
        for (Plot current : plots) {
            boolean[] merged = new boolean[] { false, false, false, false };
            current.setMerged(merged);
            if (createSign) {
                MainUtil.setSign(getName(current.owner), current);
            }
        }
        if (createRoad) {
            manager.finishPlotUnlink(plotworld, ids);
        }
        return true;
    }
    
    /**
     * Check if a location is a plot area.<br>
     *  - Directly checks the cluster manager<br>
     * @param location
     * @return
     */
    public static boolean isPlotAreaAbs(final Location location) {
        if (!Settings.ENABLE_CLUSTERS) {
            return true;
        }
        final PlotWorld plotworld = PS.get().getPlotWorld(location.getWorld());
        if (plotworld == null) {
            return false;
        }
        if (plotworld.TYPE == 2) {
            return ClusterManager.getClusterAbs(location) != null;
        }
        return true;
    }
    
    /**
     * Check if a location corresponds to a plot road i.e. A plot area but not inside a plot
     * @param location
     * @return
     */
    public static boolean isPlotRoad(final Location location) {
        final PlotWorld plotworld = PS.get().getPlotWorld(location.getWorld());
        if (plotworld.TYPE == 2) {
            final PlotCluster cluster = ClusterManager.getCluster(location);
            if (cluster == null) {
                return false;
            }
        }
        final PlotManager manager = PS.get().getPlotManager(location.getWorld());
        return manager.getPlotId(plotworld, location.getX(), location.getY(), location.getZ()) == null;
    }
    
    /**
     * Check if a plot is in a plot area.<br>
     *  - Useful as plot objects can be created with any location which may not be valid.
     * @param plot
     * @return
     */
    public static boolean isPlotArea(final Plot plot) {
        if (!Settings.ENABLE_CLUSTERS) {
            return true;
        }
        final PlotWorld plotworld = plot.getWorld();
        if (plotworld.TYPE == 2) {
            return plot.getCluster() != null;
        }
        return true;
    }
    
    /**
     * Get the number of plots for a player
     *
     * @param plr
     *
     * @return int plot count
     */
    public static int getPlayerPlotCount(final String world, final PlotPlayer plr) {
        final UUID uuid = plr.getUUID();
        int count = 0;
        for (final Plot plot : PS.get().getPlotsInWorld(world)) {
            if (plot.hasOwner() && plot.owner.equals(uuid) && (!Settings.DONE_COUNTS_TOWARDS_LIMIT || !plot.getFlags().containsKey("done"))) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get a player's total number of plots that count towards their limit
     * @param plr
     * @return
     */
    public static int getPlayerPlotCount(final PlotPlayer plr) {
        if (!Settings.GLOBAL_LIMIT) {
            return getPlayerPlotCount(plr.getLocation().getWorld(), plr);
        }
        int count = 0;
        for (final String world : PS.get().getPlotWorldsString()) {
            count += getPlayerPlotCount(world, plr);
        }
        return count;
    }
    
    /**
     * Get the base plot for a plot
     * @see Plot#getBasePlot(boolean)
     * @param plot
     * @return
     */
    public static Plot getPlot(Plot plot) {
        if (plot == null) {
            return null;
        }
        return plot.getBasePlot(false);
    }
    
    /**
     * Get the plot at a location<br>
     * @param loc
     * @return The plot at a location. The base plot will be returned if a mega plot.
     */
    public static Plot getPlot(Location loc) {
        return getPlot(getPlotAbs(loc));
    }
    
    /**
     * Get the plot given the world and plot id
     * @param world
     * @param id
     * @return The plot. The base plot will be returned if a mega plot.
     */
    public static Plot getPlot(String world, PlotId id) {
        if (id == null) {
            return null;
        }
        return getPlot(getPlotAbs(world, id));
    }
    
    /**
     * Get the default home location for a plot<br>
     *  - Ignores any home location set for that specific plot
     * @param plot
     * @return
     */
    public static Location getDefaultHome(Plot plot) {
        plot = plot.getBasePlot(false);
        final PlotWorld plotworld = plot.getWorld();
        if (plotworld.DEFAULT_HOME != null) {
            final int x;
            final int z;
            if ((plotworld.DEFAULT_HOME.x == Integer.MAX_VALUE) && (plotworld.DEFAULT_HOME.z == Integer.MAX_VALUE)) {
                // center
                RegionWrapper largest = getLargestRegion(plot);
                x = ((largest.maxX - largest.minX) / 2) + largest.minX;
                z = ((largest.maxZ - largest.minZ) / 2) + largest.minZ;
            } else {
                // specific
                Location bot = plot.getBottomAbs();
                x = bot.getX() + plotworld.DEFAULT_HOME.x;
                z = bot.getZ() + plotworld.DEFAULT_HOME.z;
            }
            final int y = getHeighestBlock(plot.world, x, z);
            return new Location(plot.world, x, y + 1, z);
        }
        // Side
        RegionWrapper largest = getLargestRegion(plot);
        final int x = ((largest.maxX - largest.minX) / 2) + largest.minX;
        final int z = largest.minZ - 1;
        final PlotManager manager = plot.getManager();
        final int y = Math.max(getHeighestBlock(plot.world, x, z), manager.getSignLoc(plot.getWorld(), plot).getY());
        return new Location(plot.world, x, y + 1, z);
    }
    
    /**
     * Teleport a player to a plot and send them the teleport message.
     * @param player
     * @param from
     * @param plot
     * @return If the teleportation is allowed.
     */
    public static boolean teleportPlayer(final PlotPlayer player, final Location from, Plot plot) {
        plot = plot.getBasePlot(false);
        final boolean result = EventUtil.manager.callTeleport(player, from, plot);
        if (result) {
            final Location location;
            if (plot.getWorld().HOME_ALLOW_NONMEMBER || plot.isAdded(player.getUUID())) {
                location = MainUtil.getPlotHome(plot);
            } else {
                location = getDefaultHome(plot);
            }
            if ((Settings.TELEPORT_DELAY == 0) || Permissions.hasPermission(player, "plots.teleport.delay.bypass")) {
                sendMessage(player, C.TELEPORTED_TO_PLOT);
                player.teleport(location);
                return true;
            }
            sendMessage(player, C.TELEPORT_IN_SECONDS, Settings.TELEPORT_DELAY + "");
            final String name = player.getName();
            TaskManager.TELEPORT_QUEUE.add(name);
            TaskManager.runTaskLater(new Runnable() {
                @Override
                public void run() {
                    if (!TaskManager.TELEPORT_QUEUE.contains(name)) {
                        sendMessage(player, C.TELEPORT_FAILED);
                        return;
                    }
                    TaskManager.TELEPORT_QUEUE.remove(name);
                    if (!player.isOnline()) {
                        return;
                    }
                    sendMessage(player, C.TELEPORTED_TO_PLOT);
                    player.teleport(location);
                }
            }, Settings.TELEPORT_DELAY * 20);
            return true;
        }
        return result;
    }
    
    /**
     * Get the plot border distance for a world<br>
     * @param worldname
     * @return The border distance or Integer.MAX_VALUE if no border is set
     */
    public static int getBorder(final String worldname) {
        if (worldBorder.containsKey(worldname)) {
            final int border = worldBorder.get(worldname) + 16;
            if (border == 0) {
                return Integer.MAX_VALUE;
            } else {
                return border;
            }
        }
        return Integer.MAX_VALUE;
    }
    
    /**
     * Setup the plot border for a world (usually done when the world is created)
     * @param world
     */
    public static void setupBorder(final String world) {
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        if (!plotworld.WORLD_BORDER) {
            return;
        }
        if (!worldBorder.containsKey(world)) {
            worldBorder.put(world, 0);
        }
        for (final Plot plot : PS.get().getPlotsInWorld(world)) {
            updateWorldBorder(plot);
        }
    }
    
    /**
     * Resend the chunk at a location
     * @param world
     * @param loc
     */
    public static void update(final String world, final ChunkLoc loc) {
        BlockUpdateUtil.setBlockManager.update(world, Collections.singletonList(loc));
    }
    
    /**
     * Resend the chunks in a plot
     * @param plot
     */
    public static void update(final Plot plot) {
        TaskManager.runTask(new Runnable() {
            @Override
            public void run() {
                final HashSet<ChunkLoc> chunks = new HashSet<>();
                for (RegionWrapper region : getRegions(plot)) {
                    for (int x = region.minX >> 4; x <= region.maxX >> 4; x++) {
                        for (int z = region.minZ >> 4; z <= region.maxZ >> 4; z++) {
                            chunks.add(new ChunkLoc(x, z));
                        }
                    }
                }
                BlockUpdateUtil.setBlockManager.update(plot.world, chunks);
            }
        });
    }
    
    /**
     * direction 0 = north, 1 = south, etc:
     *
     * @param id
     * @param direction
     *
     * @return PlotId relative
     */
    public static PlotId getPlotIdRelative(final PlotId id, final int direction) {
        switch (direction) {
            case 0:
                return new PlotId(id.x, id.y - 1);
            case 1:
                return new PlotId(id.x + 1, id.y);
            case 2:
                return new PlotId(id.x, id.y + 1);
            case 3:
                return new PlotId(id.x - 1, id.y);
        }
        return id;
    }
    
    /**
     * direction 0 = north, 1 = south, etc:
     *
     * @param plot
     * @param direction
     *
     * @return Plot relative
     */
    public static Plot getPlotRelative(final Plot plot, final int direction) {
        return getPlotAbs(plot.world, getPlotIdRelative(plot.getId(), direction));
    }
    
    /**
     * Get a list of plot ids within a selection
     * @param pos1
     * @param pos2
     * @return
     */
    public static ArrayList<PlotId> getPlotSelectionIds(final PlotId pos1, final PlotId pos2) {
        final ArrayList<PlotId> myplots = new ArrayList<>();
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                myplots.add(new PlotId(x, y));
            }
        }
        return myplots;
    }
    
    /**
     * Get a set of owned plots within a selection (chooses the best algorithm based on selection size.<br>
     * i.e. A selection of billions of plots will work fine
     * @param pos1
     * @param pos2
     * @return
     */
    public static HashSet<Plot> getPlotSelectionOwned(final String world, final PlotId pos1, final PlotId pos2) {
        final int size = ((1 + pos2.x) - pos1.x) * ((1 + pos2.y) - pos1.y);
        final HashSet<Plot> result = new HashSet<>();
        if (PS.get().isPlotWorld(world)) {
            if (size < 16 || size < PS.get().getAllPlotsRaw().get(world).size()) {
                for (final PlotId pid : MainUtil.getPlotSelectionIds(pos1, pos2)) {
                    final Plot plot = MainUtil.getPlotAbs(world, pid);
                    if (plot.hasOwner()) {
                        if ((plot.getId().x > pos1.x) || (plot.getId().y > pos1.y) || (plot.getId().x < pos2.x) || (plot.getId().y < pos2.y)) {
                            result.add(plot);
                        }
                    }
                }
            } else {
                for (final Plot plot : PS.get().getPlotsInWorld(world)) {
                    if ((plot.getId().x > pos1.x) || (plot.getId().y > pos1.y) || (plot.getId().x < pos2.x) || (plot.getId().y < pos2.y)) {
                        result.add(plot);
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Completely merges a set of plots<br> <b>(There are no checks to make sure you supply the correct
     * arguments)</b><br> - Misuse of this method can result in unusable plots<br> - the set of plots must belong to one
     * owner and be rectangular<br> - the plot array must be sorted in ascending order<br> - Road will be removed where
     * required<br> - changes will be saved to DB<br>
     *
     * @param world
     * @param plotIds
     *
     * @return boolean (success)
     */
    public static boolean mergePlots(final String world, final ArrayList<PlotId> plotIds, final boolean removeRoads, final boolean updateDatabase) {
        if (plotIds.size() < 2) {
            return false;
        }
        final PlotId pos1 = plotIds.get(0);
        final PlotId pos2 = plotIds.get(plotIds.size() - 1);
        final PlotManager manager = PS.get().getPlotManager(world);
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        
        final boolean result = EventUtil.manager.callMerge(world, getPlotAbs(world, pos1), plotIds);
        if (!result) {
            return false;
        }
        
        final HashSet<UUID> trusted = new HashSet<UUID>();
        final HashSet<UUID> members = new HashSet<UUID>();
        final HashSet<UUID> denied = new HashSet<UUID>();
        
        manager.startPlotMerge(plotworld, plotIds);
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final PlotId id = new PlotId(x, y);
                final Plot plot = PS.get().getPlot(world, id);
                trusted.addAll(plot.getTrusted());
                members.addAll(plot.getMembers());
                denied.addAll(plot.getDenied());
                if (removeRoads) {
                    removeSign(plot);
                }
            }
        }
        members.removeAll(trusted);
        denied.removeAll(trusted);
        denied.removeAll(members);
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final boolean lx = x < pos2.x;
                final boolean ly = y < pos2.y;
                final PlotId id = new PlotId(x, y);
                final Plot plot = PS.get().getPlot(world, id);
                plot.setTrusted(trusted);
                plot.setMembers(members);
                plot.setDenied(denied);
                Plot plot2 = null;
                if (lx) {
                    if (ly) {
                        if (!plot.getMerged(1) || !plot.getMerged(2)) {
                            if (removeRoads) {
                                MainUtil.removeRoadSouthEast(plotworld, plot);
                            }
                        }
                    }
                    if (!plot.getMerged(1)) {
                        plot2 = PS.get().getPlot(world, new PlotId(x + 1, y));
                        mergePlot(world, plot, plot2, removeRoads);
                    }
                }
                if (ly) {
                    if (!plot.getMerged(2)) {
                        plot2 = PS.get().getPlot(world, new PlotId(x, y + 1));
                        mergePlot(world, plot, plot2, removeRoads);
                    }
                }
            }
        }
        manager.finishPlotMerge(plotworld, plotIds);
        return true;
    }
    
    /**
     * Remove the south east road section of a plot<br>
     *  - Used when a plot is merged<br>
     * @param plotworld
     * @param plot
     */
    public static void removeRoadSouthEast(final PlotWorld plotworld, final Plot plot) {
        if ((plotworld.TYPE != 0) && (plotworld.TERRAIN > 1)) {
            if (plotworld.TERRAIN == 3) {
                return;
            }
            final PlotId id = plot.getId();
            final PlotId id2 = new PlotId(id.x + 1, id.y + 1);
            final Location pos1 = getPlotTopLocAbs(plot.world, id).add(1, 0, 1);
            final Location pos2 = getPlotBottomLocAbs(plot.world, id2).subtract(1, 0, 1);
            pos1.setY(0);
            pos2.setY(256);
            ChunkManager.manager.regenerateRegion(pos1, pos2, null);
        } else {
            plot.getManager().removeRoadSouthEast(plotworld, plot);
        }
    }
    
    /**
     * Remove the east road section of a plot<br>
     *  - Used when a plot is merged<br>
     * @param plotworld
     * @param plot
     */
    public static void removeRoadEast(final PlotWorld plotworld, final Plot plot) {
        if ((plotworld.TYPE != 0) && (plotworld.TERRAIN > 1)) {
            if (plotworld.TERRAIN == 3) {
                return;
            }
            final PlotId id = plot.getId();
            final PlotId id2 = new PlotId(id.x + 1, id.y);
            final Location bot = getPlotBottomLocAbs(plot.world, id2);
            final Location top = getPlotTopLocAbs(plot.world, id);
            final Location pos1 = new Location(plot.world, top.getX(), 0, bot.getZ());
            final Location pos2 = new Location(plot.world, bot.getX(), 256, top.getZ());
            ChunkManager.manager.regenerateRegion(pos1, pos2, null);
        } else {
            plot.getManager().removeRoadEast(plotworld, plot);
        }
    }
    
    /**
     * Remove the south road section of a plot<br>
     *  - Used when a plot is merged<br>
     * @param plotworld
     * @param plot
     */
    public static void removeRoadSouth(final PlotWorld plotworld, final Plot plot) {
        if ((plotworld.TYPE != 0) && (plotworld.TERRAIN > 1)) {
            if (plotworld.TERRAIN == 3) {
                return;
            }
            final PlotId id = plot.getId();
            final PlotId id2 = new PlotId(id.x, id.y + 1);
            final Location bot = getPlotBottomLocAbs(plot.world, id2);
            final Location top = getPlotTopLocAbs(plot.world, id);
            final Location pos1 = new Location(plot.world, bot.getX(), 0, top.getZ());
            final Location pos2 = new Location(plot.world, top.getX(), 256, bot.getZ());
            ChunkManager.manager.regenerateRegion(pos1, pos2, null);
        } else {
            plot.getManager().removeRoadSouth(plotworld, plot);
        }
    }
    
    /**
     * Merges 2 plots Removes the road inbetween <br>- Assumes plots are directly next to each other <br> - saves to DB
     *
     * @param world
     * @param lesserPlot
     * @param greaterPlot
     */
    public static void mergePlot(final String world, Plot lesserPlot, Plot greaterPlot, final boolean removeRoads) {
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        if (lesserPlot.getId().x.equals(greaterPlot.getId().x)) {
            if (lesserPlot.getId().y > greaterPlot.getId().y) {
                Plot tmp = lesserPlot;
                lesserPlot = greaterPlot;
                greaterPlot = tmp;
            }
            if (!lesserPlot.getMerged(2)) {
                lesserPlot.clearRatings();
                greaterPlot.clearRatings();
                lesserPlot.setMerged(2, true);
                greaterPlot.setMerged(0, true);
                mergeData(lesserPlot, greaterPlot);
                if (removeRoads) {
                    if (lesserPlot.getMerged(5)) {
                        removeRoadSouthEast(plotworld, lesserPlot);
                    }
                    MainUtil.removeRoadSouth(plotworld, lesserPlot);
                    Plot other = getPlotAbs(world, getPlotIdRelative(lesserPlot.getId(), 3));
                    if (other.getMerged(2) && other.getMerged(1)) {
                        MainUtil.removeRoadSouthEast(plotworld, other);
                        mergePlot(world, greaterPlot, getPlotAbs(world, getPlotIdRelative(greaterPlot.getId(), 3)), removeRoads);
                    }
                }
            }
        } else {
            if (lesserPlot.getId().x > greaterPlot.getId().x) {
                Plot tmp = lesserPlot;
                lesserPlot = greaterPlot;
                greaterPlot = tmp;
            }
            if (!lesserPlot.getMerged(1)) {
                lesserPlot.clearRatings();
                greaterPlot.clearRatings();
                lesserPlot.setMerged(1, true);
                greaterPlot.setMerged(3, true);
                mergeData(lesserPlot, greaterPlot);
                if (removeRoads) {
                    MainUtil.removeRoadEast(plotworld, lesserPlot);
                    if (lesserPlot.getMerged(5)) {
                        removeRoadSouthEast(plotworld, lesserPlot);
                    }
                    Plot other = getPlotAbs(world, getPlotIdRelative(lesserPlot.getId(), 0));
                    if (other.getMerged(2) && other.getMerged(1)) {
                        MainUtil.removeRoadSouthEast(plotworld, other);
                        mergePlot(world, greaterPlot, getPlotAbs(world, getPlotIdRelative(greaterPlot.getId(), 0)), removeRoads);
                    }
                }
            }
        }
    }
    
    /**
     * Merge the plot settings<br>
     *  - Used when a plot is merged<br>
     * @param a
     * @param b
     */
    public static void mergeData(Plot a, Plot b) {
        HashMap<String, Flag> flags1 = a.getFlags();
        HashMap<String, Flag> flags2 = b.getFlags();
        if ((flags1.size() != 0 || flags2.size() != 0) && !flags1.equals(flags2)) {
            boolean greater = flags1.size() > flags2.size();
            if (greater) {
                flags1.putAll(flags2);
            }
            else {
                flags2.putAll(flags1);
            }
            HashSet<Flag> net = new HashSet<>((greater ? flags1 : flags2).values());
            a.setFlags(net);
            b.setFlags(net);
        }
        if (a.getAlias().length() > 0) {
            b.setAlias(a.getAlias());
        }
        else if (b.getAlias().length() > 0) {
            a.setAlias(b.getAlias());
        }
        for (UUID uuid : a.getTrusted()) {
            b.addTrusted(uuid);
        }
        for (UUID uuid : b.getTrusted()) {
            a.addTrusted(uuid);
        }
        for (UUID uuid : a.getMembers()) {
            b.addMember(uuid);
        }
        for (UUID uuid : b.getMembers()) {
            a.addMember(uuid);
        }
        
        for (UUID uuid : a.getDenied()) {
            b.addDenied(uuid);
        }
        for (UUID uuid : b.getDenied()) {
            a.addDenied(uuid);
        }
    }
    
    /**
     * Remove the sign for a plot
     * @param p
     */
    public static void removeSign(final Plot p) {
        if (!PS.get().isMainThread(Thread.currentThread())) {
            TaskManager.runTask(new Runnable() {
                @Override
                public void run() {
                    removeSign(p);
                }
            });
            return;
        }
        final String world = p.world;
        final PlotManager manager = PS.get().getPlotManager(world);
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        if (!plotworld.ALLOW_SIGNS) {
            return;
        }
        final Location loc = manager.getSignLoc(plotworld, p);
        BlockManager.setBlocks(world, new int[] { loc.getX() }, new int[] { loc.getY() }, new int[] { loc.getZ() }, new int[] { 0 }, new byte[] { 0 });
    }
    
    /**
     * Set the sign for a plot
     * @param p
     */
    public static void setSign(final Plot p) {
        if (p.owner == null) {
            setSign(null, p);
            return;
        }
        setSign(UUIDHandler.getName(p.owner), p);
    }
    
    /**
     * Set the sign for a plot to a specific name
     * @param name
     * @param p
     */
    public static void setSign(final String name, final Plot p) {
        if (!PS.get().isMainThread(Thread.currentThread())) {
            TaskManager.runTask(new Runnable() {
                @Override
                public void run() {
                    setSign(name, p);
                }
            });
            return;
        }
        final String rename = name == null ? "unknown" : name;
        final PlotManager manager = p.getManager();
        final PlotWorld plotworld = p.getWorld();
        if (plotworld.ALLOW_SIGNS) {
            final Location loc = manager.getSignLoc(plotworld, p);
            final String id = p.getId().x + ";" + p.getId().y;
            final String[] lines = new String[] {
            C.OWNER_SIGN_LINE_1.formatted().replaceAll("%id%", id),
            C.OWNER_SIGN_LINE_2.formatted().replaceAll("%id%", id).replaceAll("%plr%", rename),
            C.OWNER_SIGN_LINE_3.formatted().replaceAll("%id%", id).replaceAll("%plr%", rename),
            C.OWNER_SIGN_LINE_4.formatted().replaceAll("%id%", id).replaceAll("%plr%", rename) };
            BlockManager.setSign(p.world, loc.getX(), loc.getY(), loc.getZ(), lines);
        }
    }
    
    /**
     * Get the corner locations for a plot<br>
     * @see Plot#getCorners() 
     * @param world
     * @param region
     * @return
     */
    public static Location[] getCorners(String world, RegionWrapper region) {
        Location pos1 = new Location(world, region.minX, region.minY, region.minZ);
        Location pos2 = new Location(world, region.maxX, region.maxY, region.maxZ);
        return new Location[] { pos1, pos2 };
    }
    
    /**
     * Returns the top and bottom connected plot.<br>
     *  - If the plot is not connected, it will return itself for the top/bottom<br>
     *  - the returned IDs will not necessarily correspond to claimed plots if the connected plots do not form a rectangular shape 
     * @param plot
     * @return new PlotId[] { bottom, top }
     */
    public static Location[] getCorners(Plot plot) {
        if (!plot.isMerged()) {
            return new Location[] { plot.getBottomAbs(), plot.getTopAbs() };
        }
        return getCorners(plot.world, getRegions(plot));
    }
    
    /**
     * Get the corner locations for a list of regions<br>
     * @see Plot#getCorners() 
     * @param world
     * @param regions
     * @return
     */
    public static Location[] getCorners(String world, Collection<RegionWrapper> regions) {
        Location min = null;
        Location max = null;
        for (RegionWrapper region : regions) {
            Location[] corners = getCorners(world, region);
            if (min == null) {
                min = corners[0];
                max = corners[1];
                continue;
            }
            Location pos1 = corners[0];
            Location pos2 = corners[1];
            if (pos2.getX() > max.getX()) {
                max.setX(pos2.getX());
            }
            if (pos1.getX() < min.getX()) {
                min.setX(pos1.getX());
            }
            if (pos2.getZ() > max.getZ()) {
                max.setZ(pos2.getZ());
            }
            if (pos1.getZ() < min.getZ()) {
                min.setZ(pos1.getZ());
            }
        }
        return new Location[] { min, max };
    }
    
    /**
     * Get the corner plot ids for a plot<br>
     * @see Plot#getCornerIds()
     * @param plot
     * @return
     */
    public static PlotId[] getCornerIds(Plot plot) {
        if (!plot.isMerged()) {
            return new PlotId[] { plot.getId(), plot.getId() };
        }
        PlotId min = new PlotId(plot.getId().x, plot.getId().y);
        PlotId max = new PlotId(plot.getId().x, plot.getId().y);
        for (Plot current : getConnectedPlots(plot)) {
            if (current.getId().x < min.x) {
                min.x = current.getId().x;
            }
            else if (current.getId().x > max.x) {
                max.x = current.getId().x;
            }
            if (current.getId().y < min.y) {
                min.y = current.getId().y;
            }
            else if (current.getId().y > max.y) {
                max.y = current.getId().y;
            }
        }
        return new PlotId[] { min, max };
    }
    
    /**
     * Auto merge a plot in a specific direction<br>
     * @param plot The plot to merge
     * @param dir The direction to merge<br>
     * -1 = All directions<br>
     * 0 = north<br>
     * 1 = east<br>
     * 2 = south<br>
     * 3 = west<br>
     * @param max The max number of merges to do
     * @param uuid The UUID it is allowed to merge with
     * @param removeRoads Wether to remove roads
     * @return true if a merge takes place
     */
    public static boolean autoMerge(final Plot plot, int dir, int max, final UUID uuid, final boolean removeRoads) {
        if (plot == null) {
            return false;
        }
        if (plot.owner == null) {
            return false;
        }
        HashSet<Plot> visited = new HashSet<>();
        HashSet<PlotId> merged = new HashSet<>();
        for (Plot current : getConnectedPlots(plot)) {
            merged.add(current.getId());
        }
        ArrayDeque<Plot> frontier = new ArrayDeque<>(getConnectedPlots(plot));
        Plot current;
        boolean toReturn = false;
        Set<Plot> plots;
        while ((current = frontier.poll()) != null && max >= 0) {
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);
            if (max >= 0 && (dir == -1 || dir == 0) && !current.getMerged(0)) {
                Plot other = getPlotRelative(current, 0);
                if (other.isOwner(uuid) && (other.getBasePlot(false).equals(current.getBasePlot(false)) || ((plots = other.getConnectedPlots()).size() <= max && frontier.addAll(plots) && (max -= plots.size()) != -1))) {
                    mergePlot(current.world, current, other, removeRoads);
                    merged.add(current.getId());
                    merged.add(other.getId());
                    toReturn = true;
                }
            }
            if (max >= 0 && (dir == -1 || dir == 1) && !current.getMerged(1)) {
                Plot other = getPlotRelative(current, 1);
                if (other.isOwner(uuid) && (other.getBasePlot(false).equals(current.getBasePlot(false)) || ((plots = other.getConnectedPlots()).size() <= max && frontier.addAll(plots) && (max -= plots.size()) != -1))) {
                    mergePlot(current.world, current, other, removeRoads);
                    merged.add(current.getId());
                    merged.add(other.getId());
                    toReturn = true;
                }
            }
            if (max >= 0 && (dir == -1 || dir == 2) && !current.getMerged(2)) {
                Plot other = getPlotRelative(current, 2);
                if (other.isOwner(uuid) && (other.getBasePlot(false).equals(current.getBasePlot(false)) || ((plots = other.getConnectedPlots()).size() <= max && frontier.addAll(plots) && (max -= plots.size()) != -1))) {
                    mergePlot(current.world, current, other, removeRoads);
                    merged.add(current.getId());
                    merged.add(other.getId());
                    toReturn = true;
                }
            }
            if (max >= 0 && (dir == -1 || dir == 3) && !current.getMerged(3)) {
                Plot other = getPlotRelative(current, 3);
                if (other.isOwner(uuid) && (other.getBasePlot(false).equals(current.getBasePlot(false)) || ((plots = other.getConnectedPlots()).size() <= max && frontier.addAll(plots) && (max -= plots.size()) != -1))) {
                    mergePlot(current.world, current, other, removeRoads);
                    merged.add(current.getId());
                    merged.add(other.getId());
                    toReturn = true;
                }
            }
        }
        if (removeRoads && toReturn) {
            PlotManager manager = plot.getManager();
            ArrayList<PlotId> ids = new ArrayList<>(merged);
            manager.finishPlotMerge(plot.getWorld(), ids);
        }
        return toReturn;
    }

    /**
     * Expand the world border to include the provided plot (if applicable)
     * @param plot
     */
    public static void updateWorldBorder(final Plot plot) {
        if (!plot.hasOwner() || !worldBorder.containsKey(plot.world)) {
            return;
        }
        final String world = plot.world;
        final PlotManager manager = PS.get().getPlotManager(world);
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        final PlotId id = new PlotId(Math.abs(plot.getId().x) + 1, Math.abs(plot.getId().x) + 1);
        final Location bot = manager.getPlotBottomLocAbs(plotworld, id);
        final Location top = manager.getPlotTopLocAbs(plotworld, id);
        final int border = worldBorder.get(plot.world);
        final int botmax = Math.max(Math.abs(bot.getX()), Math.abs(bot.getZ()));
        final int topmax = Math.max(Math.abs(top.getX()), Math.abs(top.getZ()));
        final int max = Math.max(botmax, topmax);
        if (max > border) {
            worldBorder.put(plot.world, max);
        }
    }
    
    /**
     * Create a plot and notify the world border and plot merger
     */
    public static Plot createPlot(final UUID uuid, final Plot plot) {
        System.out.println("CLAIMING PLOT 1");
        if (uuid == null) {
            return null;
        }
        System.out.println("CLAIMING PLOT 2");
        Plot existing = PS.get().getPlot(plot.world, plot.id);
        if (existing != null) {
            return existing;
        }
        if (MainUtil.worldBorder.containsKey(plot.world)) {
            updateWorldBorder(plot);
        }
        plot.owner = uuid;
        plot.getTrusted().clear();
        plot.getMembers().clear();
        plot.getDenied().clear();
        plot.settings = new PlotSettings();
        if (PS.get().updatePlot(plot)) {
            DBFunc.createPlotAndSettings(plot, new Runnable() {
                @Override
                public void run() {
                    final PlotWorld plotworld = plot.getWorld();
                    if (plotworld.AUTO_MERGE) {
                        autoMerge(plot, -1, Integer.MAX_VALUE, uuid, true);
                    }
                }
            });
        }
        return plot;
    }
    
    /**
     * Create a plot without notifying the merge function or world border manager
     */
    public static Plot createPlotAbs(final UUID uuid, final Plot plot) {
        if (uuid == null) {
            return null;
        }
        Plot existing = getPlot(plot.world, plot.id);
        if (existing != null) {
            return existing;
        }
        if (MainUtil.worldBorder.containsKey(plot.world)) {
            updateWorldBorder(plot);
        }
        plot.owner = uuid;
        plot.getTrusted().clear();
        plot.getMembers().clear();
        plot.getDenied().clear();
        plot.settings = new PlotSettings();
        if (PS.get().updatePlot(plot)) {
            DBFunc.createPlotAndSettings(plot, new Runnable() {
                @Override
                public void run() {
                    final PlotWorld plotworld = plot.getWorld();
                    if (plotworld.AUTO_MERGE) {
                        autoMerge(plot, -1, Integer.MAX_VALUE, uuid, true);
                    }
                }
            });
        }
        return plot;
    }
    
    /**
     * Clear a plot and associated sections: [sign, entities, border]
     *
     * @param plot
     * @param isDelete
     * @param whenDone
     */
    public static boolean clearAsPlayer(final Plot plot, final boolean isDelete, final Runnable whenDone) {
        if (plot.getRunning() != 0) {
            return false;
        }
        clear(plot, isDelete, whenDone);
        return true;
    }
    
    /**
     * Count the entities in a plot
     * @see ChunkManager#countEntities(Plot)
     * 0 = Entity
     * 1 = Animal
     * 2 = Monster
     * 3 = Mob
     * 4 = Boat
     * 5 = Misc
     * @param plot
     * @return
     */
    public static int[] countEntities(Plot plot) {
        int[] count = new int[6];
        for (Plot current : getConnectedPlots(plot)) {
            int[] result = ChunkManager.manager.countEntities(current);
            count[0] += result[0];
            count[1] += result[1];
            count[2] += result[2];
            count[3] += result[3];
            count[4] += result[4];
            count[5] += result[5];
        }
        return count;
    }
    
    /**
     * Clear and unclaim a plot
     * @see Plot#clear(Runnable)
     * @param plot
     * @param whenDone
     * @return
     */
    public static boolean delete(final Plot plot, final Runnable whenDone) {
        // Plot is not claimed
        if (!plot.hasOwner()) {
            return false;
        }
        final HashSet<Plot> plots = getConnectedPlots(plot);
        clear(plot, true, new Runnable() {
            @Override
            public void run() {
                for (Plot current : plots) {
                    current.unclaim();
                }
                TaskManager.runTask(whenDone);
            }
        });
        return true;
    }
    
    /**
     * Clear a plot (does not remove from database)
     * @param plot
     * @param isDelete Different procedures take place if the clearing is a deletion:<br>
     *  - The sign, border and walls are also cleared on deletion<br>
     * @param whenDone A runnable that will execute when the clearing is done, or null
     * @return
     */
    public static boolean clear(final Plot plot, final boolean isDelete, final Runnable whenDone) {
        if (!EventUtil.manager.callClear(plot.world, plot.getId())) {
            return false;
        }
        final HashSet<RegionWrapper> regions = getRegions(plot);
        final HashSet<Plot> plots = getConnectedPlots(plot);
        final ArrayDeque<Plot> queue = new ArrayDeque<>(plots);
        if (isDelete) {
            removeSign(plot);
        }
        MainUtil.unlinkPlot(plot, true, !isDelete);
        final PlotManager manager = plot.getManager();
        final PlotWorld plotworld = plot.getWorld();
        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (queue.size() == 0) {
                    final AtomicInteger finished = new AtomicInteger(0);
                    final Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            if (finished.incrementAndGet() >= plots.size()) {
                                for (RegionWrapper region : regions) {
                                    Location[] corners = getCorners(plot.world, region);
                                    ChunkManager.manager.clearAllEntities(corners[0], corners[1]);
                                }
                                TaskManager.runTask(whenDone);
                            }
                        }
                    };
                    if (isDelete) {
                        for (Plot current : plots) {
                            manager.unclaimPlot(plotworld, current, run);
                        }
                    }
                    else {
                        for (Plot current : plots) {
                            manager.claimPlot(plotworld, current);
                            SetBlockQueue.addNotify(run);
                        }
                    }
                    return;
                }
                Plot current = queue.poll();
                if ((plotworld.TERRAIN != 0) || Settings.FAST_CLEAR) {
                    ChunkManager.manager.regenerateRegion(current.getBottomAbs(), current.getTopAbs(), this);
                    return;
                }
                manager.clearPlot(plotworld, current, this);
            }
        };
        run.run();
        return true;
    }
    
    /**
     * Set a cuboid in the world to a set of blocks.
     * @param world
     * @param pos1
     * @param pos2
     * @param blocks If multiple blocks are provided, the result will be a random mix
     */
    public static void setCuboid(final String world, final Location pos1, final Location pos2, final PlotBlock[] blocks) {
        if (blocks.length == 1) {
            setSimpleCuboid(world, pos1, pos2, blocks[0]);
            return;
        }
        final int length = (pos2.getX() - pos1.getX()) * (pos2.getY() - pos1.getY()) * (pos2.getZ() - pos1.getZ());
        final int[] xl = new int[length];
        final int[] yl = new int[length];
        final int[] zl = new int[length];
        final int[] ids = new int[length];
        final byte[] data = new byte[length];
        int index = 0;
        for (int y = pos1.getY(); y <= pos2.getY(); y++) {
            for (int x = pos1.getX(); x <= pos2.getX(); x++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    final int i = random.random(blocks.length);
                    xl[index] = x;
                    yl[index] = y;
                    zl[index] = z;
                    final PlotBlock block = blocks[i];
                    ids[index] = block.id;
                    data[index] = block.data;
                    index++;
                }
            }
        }
        BlockManager.setBlocks(world, xl, yl, zl, ids, data);
    }
    
    /**
     * Set a cubioid asynchronously to a set of blocks
     * @param world
     * @param pos1
     * @param pos2
     * @param blocks
     */
    public static void setCuboidAsync(final String world, final Location pos1, final Location pos2, final PlotBlock[] blocks) {
        if (blocks.length == 1) {
            setSimpleCuboidAsync(world, pos1, pos2, blocks[0]);
            return;
        }
        for (int y = pos1.getY(); y <= Math.min(255, pos2.getY()); y++) {
            for (int x = pos1.getX(); x <= pos2.getX(); x++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    final int i = random.random(blocks.length);
                    final PlotBlock block = blocks[i];
                    SetBlockQueue.setBlock(world, x, y, z, block);
                }
            }
        }
    }
    
    /**
     * Set a cuboid to a block
     * @param world
     * @param pos1
     * @param pos2
     * @param newblock
     */
    public static void setSimpleCuboid(final String world, final Location pos1, final Location pos2, final PlotBlock newblock) {
        final int length = (pos2.getX() - pos1.getX()) * (pos2.getY() - pos1.getY()) * (pos2.getZ() - pos1.getZ());
        final int[] xl = new int[length];
        final int[] yl = new int[length];
        final int[] zl = new int[length];
        final int[] ids = new int[length];
        final byte[] data = new byte[length];
        int index = 0;
        for (int y = pos1.getY(); y <= Math.min(255, pos2.getY()); y++) {
            for (int x = pos1.getX(); x <= pos2.getX(); x++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    xl[index] = x;
                    yl[index] = y;
                    zl[index] = z;
                    ids[index] = newblock.id;
                    data[index] = newblock.data;
                    index++;
                }
            }
        }
        BlockManager.setBlocks(world, xl, yl, zl, ids, data);
    }
    
    /**
     * Set a cuboic asynchronously to a block
     * @param world
     * @param pos1
     * @param pos2
     * @param newblock
     */
    public static void setSimpleCuboidAsync(final String world, final Location pos1, final Location pos2, final PlotBlock newblock) {
        for (int y = pos1.getY(); y <= Math.min(255, pos2.getY()); y++) {
            for (int x = pos1.getX(); x <= pos2.getX(); x++) {
                for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                    SetBlockQueue.setBlock(world, x, y, z, newblock);
                }
            }
        }
    }
    
    /**
     * Set the biome for a plot asynchronously
     * @param plot
     * @param biome The biome e.g. "forest"
     * @param whenDone The task to run when finished, or null
     */
    public static void setBiome(final Plot plot, final String biome, final Runnable whenDone) {
        final ArrayDeque<RegionWrapper> regions = new ArrayDeque<>(getRegions(plot));
        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (regions.size() == 0) {
                    update(plot);
                    TaskManager.runTask(whenDone);
                    return;
                }
                RegionWrapper region = regions.poll();
                Location pos1 = new Location(plot.world, region.minX, region.minY, region.minZ);
                Location pos2 = new Location(plot.world, region.maxX, region.maxY, region.maxZ);
                ChunkManager.chunkTask(pos1, pos2, new RunnableVal<int[]>() {
                    @Override
                    public void run() {
                        final ChunkLoc loc = new ChunkLoc(value[0], value[1]);
                        ChunkManager.manager.loadChunk(plot.world, loc, false);
                        setBiome(plot.world, value[2], value[3], value[4], value[5], biome);
                        ChunkManager.manager.unloadChunk(plot.world, loc, true, true);
                    }
                }, this, 5);
                
            }
        };
        run.run();
    }
    
    /**
     * Synchronously set the biome in a selection
     * @param world
     * @param p1x
     * @param p1z
     * @param p2x
     * @param p2z
     * @param biome
     */
    public static void setBiome(final String world, final int p1x, final int p1z, final int p2x, final int p2z, final String biome) {
        final int length = ((p2x - p1x) + 1) * ((p2z - p1z) + 1);
        final int[] xl = new int[length];
        final int[] zl = new int[length];
        int index = 0;
        for (int x = p1x; x <= p2x; x++) {
            for (int z = p1z; z <= p2z; z++) {
                xl[index] = x;
                zl[index] = z;
                index++;
            }
        }
        BlockManager.setBiomes(world, xl, zl, biome);
    }
    
    /**
     * Get the heighest block at a location
     * @param world
     * @param x
     * @param z
     * @return
     */
    public static int getHeighestBlock(final String world, final int x, final int z) {
        final int result = BlockManager.manager.getHeighestBlock(world, x, z);
        if (result == 0) {
            return 64;
        }
        return result;
    }
    
    /**
     * Get plot home
     *
     * @param w      World in which the plot is located
     * @param plotid Plot ID
     *
     * @return Home Location
     */
    public static Location getPlotHome(final String w, final PlotId plotid) {
        final Plot plot = getPlot(w, plotid).getBasePlot(false);
        final BlockLoc home = plot.getPosition();
        PS.get().getPlotManager(w);
        if ((home == null) || ((home.x == 0) && (home.z == 0))) {
            return getDefaultHome(plot);
        } else {
            Location bot = plot.getBottomAbs();
            final Location loc = new Location(bot.getWorld(), bot.getX() + home.x, bot.getY() + home.y, bot.getZ() + home.z, home.yaw, home.pitch);
            if (BlockManager.manager.getBlock(loc).id != 0) {
                loc.setY(Math.max(getHeighestBlock(w, loc.getX(), loc.getZ()), bot.getY()));
            }
            return loc;
        }
    }
    
    /**
     * Get the plot home
     *
     * @param plot Plot Object
     *
     * @return Plot Home Location
     *
     */
    public static Location getPlotHome(final Plot plot) {
        return getPlotHome(plot.world, plot.getId());
    }
    
    /**
     * Gets the top plot location of a plot (all plots are treated as small plots) - To get the top loc of a mega plot
     * use getPlotTopLoc(...)
     *
     * @param world
     * @param id
     *
     * @return Location top
     */
    public static Location getPlotTopLocAbs(final String world, final PlotId id) {
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        final PlotManager manager = PS.get().getPlotManager(world);
        return manager.getPlotTopLocAbs(plotworld, id);
    }
    
    /**
     * Gets the bottom plot location of a plot (all plots are treated as small plots) - To get the top loc of a mega
     * plot use getPlotBottomLoc(...)
     *
     * @param world
     * @param id
     *
     * @return Location bottom
     */
    public static Location getPlotBottomLocAbs(final String world, final PlotId id) {
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        final PlotManager manager = PS.get().getPlotManager(world);
        return manager.getPlotBottomLocAbs(plotworld, id);
    }
    
    /**
     * Gets the top loc of a plot (if mega, returns top loc of that mega plot) - If you would like each plot treated as
     * a small plot use getPlotTopLocAbs(...)
     *
     * @param plot
     * @return Location top of mega plot
     */
    public static Location getPlotTopLoc_(Plot plot) {
        Location top = getPlotTopLocAbs(plot.world, plot.getId());
        if (!plot.isMerged()) {
            return top;
        }
        PlotId id;
        if (plot.getMerged(2)) {
            id = getPlotIdRelative(plot.getId(), 2);
            top.setZ(getPlotBottomLocAbs(plot.world, id).getZ() - 1);
        }
        if (plot.getMerged(1)) {
            id = getPlotIdRelative(plot.getId(), 1);
            top.setX(getPlotBottomLocAbs(plot.world, id).getX() - 1);
        }
        return top;
    }
    
    /**
     * Gets the bottom location for a plot.<br>
     *  - Does not respect mega plots<br>
     *  - Merged plots, only the road will be considered part of the plot<br>
     *
     * @param plot
     *
     * @return Location bottom of mega plot
     */
    public static Location getPlotBottomLoc_(Plot plot) {
        Location bot = getPlotBottomLocAbs(plot.world, plot.getId());
        if (!plot.isMerged()) {
            return bot;
        }
        PlotId id;
        if (plot.getMerged(0)) {
            id = getPlotIdRelative(plot.getId(), 0);
            bot.setZ(getPlotTopLocAbs(plot.world, id).getZ() + 1);
        }
        if (plot.getMerged(3)) {
            id = getPlotIdRelative(plot.getId(), 3);
            bot.setX(getPlotTopLocAbs(plot.world, id).getX() + 1);
        }
        return bot;
    }
    
    /**
     * Check if a selection of plots can be claimed
     * @param player
     * @param world
     * @param pos1
     * @param pos2
     * @return
     */
    public static boolean canClaim(final PlotPlayer player, final String world, final PlotId pos1, final PlotId pos2) {
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final PlotId id = new PlotId(x, y);
                final Plot plot = getPlotAbs(world, id);
                if (!canClaim(player, plot)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Check if a plot can be claimed
     * @param player
     * @param plot
     * @return
     */
    public static boolean canClaim(final PlotPlayer player, final Plot plot) {
        if (plot == null) {
            return false;
        }
        if (Settings.ENABLE_CLUSTERS) {
            final PlotCluster cluster = plot.getCluster();
            if (cluster != null) {
                if (!cluster.isAdded(player.getUUID()) && !Permissions.hasPermission(player, "plots.admin.command.claim")) {
                    return false;
                }
            }
        }
        return guessOwner(plot) == null;
    }
    
    /**
     * Try to guess who the plot owner is:
     *  - Checks cache
     *  - Checks sign text
     * @param plot
     * @return
     */
    public static UUID guessOwner(Plot plot) {
        if (plot.owner != null) {
            return plot.owner;
        }
        PlotWorld pw = plot.getWorld();
        if (!pw.ALLOW_SIGNS) {
            return null;
        }
        try {
            Location loc = plot.getManager().getSignLoc(pw, plot);
            ChunkManager.manager.loadChunk(loc.getWorld(), loc.getChunkLoc(), false);
            String[] lines = BlockManager.manager.getSign(loc);
            if (lines == null) {
                return null;
            }
            loop: for (int i = 4; i > 0; i--) {
                String caption = C.valueOf("OWNER_SIGN_LINE_" + i).s();
                int index = caption.indexOf("%plr%");
                if (index == -1) {
                    continue;
                }
                String name = lines[i - 1].substring(index);
                if (name.length() == 0) {
                    return null;
                }
                UUID owner = UUIDHandler.getUUID(name, null);
                if (owner != null) {
                    plot.owner = owner;
                    break;
                }
                if (lines[i - 1].length() == 15) {
                    BiMap<StringWrapper, UUID> map = UUIDHandler.getUuidMap();
                    for (Entry<StringWrapper, UUID> entry : map.entrySet()) {
                        String key = entry.getKey().value;
                        if (key.length() > name.length() && key.startsWith(name)) {
                            plot.owner = entry.getValue();
                            break loop;
                        }
                    }
                }
                plot.owner = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                break;
            }
            if (plot.owner != null) {
                plot.create();
            }
            return plot.owner;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if the plots in a selection are unowned
     * @param world
     * @param pos1
     * @param pos2
     * @return
     */
    public static boolean isUnowned(final String world, final PlotId pos1, final PlotId pos2) {
        for (int x = pos1.x; x <= pos2.x; x++) {
            for (int y = pos1.y; y <= pos2.y; y++) {
                final PlotId id = new PlotId(x, y);
                if (PS.get().getPlot(world, id) != null) {
                    if (PS.get().getPlot(world, id).owner != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * Swap the settings for two plots
     * @param p1
     * @param p2
     * @param whenDone
     * @return
     */
    public static boolean swapData(Plot p1, Plot p2, final Runnable whenDone) {
        if ((p1 == null) || (p1.owner == null)) {
            if ((p2 != null) && (p2.owner != null)) {
                moveData(p2, p1, whenDone);
                return true;
            }
            return false;
        }
        if ((p2 == null) || (p2.owner == null)) {
            if ((p1 != null) && (p1.owner != null)) {
                moveData(p1, p2, whenDone);
                return true;
            }
            return false;
        }
        // Swap cached
        final PlotId temp = new PlotId(p1.getId().x, p1.getId().y);
        p1.getId().x = p2.getId().x;
        p1.getId().y = p2.getId().y;
        p2.getId().x = temp.x;
        p2.getId().y = temp.y;
        final Map<String, ConcurrentHashMap<PlotId, Plot>> raw = PS.get().getAllPlotsRaw();
        raw.get(p1.world).remove(p1.getId());
        raw.get(p2.world).remove(p2.getId());
        p1.getId().recalculateHash();
        p2.getId().recalculateHash();
        raw.get(p1.world).put(p1.getId(), p1);
        raw.get(p2.world).put(p2.getId(), p2);
        // Swap database
        DBFunc.dbManager.swapPlots(p2, p1);
        TaskManager.runTaskLater(whenDone, 1);
        return true;
    }
    
    /**
     * Move the settings for a plot
     * @param pos1
     * @param pos2
     * @param whenDone
     * @return
     */
    public static boolean moveData(final Plot pos1, final Plot pos2, final Runnable whenDone) {
        if (pos1.owner == null) {
            PS.debug(pos2 + " is unowned (single)");
            TaskManager.runTask(whenDone);
            return false;
        }
        if (pos2.hasOwner()) {
            PS.debug(pos2 + " is unowned (multi)");
            TaskManager.runTask(whenDone);
            return false;
        }
        final Map<String, ConcurrentHashMap<PlotId, Plot>> raw = PS.get().getAllPlotsRaw();
        raw.get(pos1.world).remove(pos1.getId());
        pos1.getId().x = (int) pos2.getId().x;
        pos1.getId().y = (int) pos2.getId().y;
        pos1.getId().recalculateHash();
        raw.get(pos2.world).put(pos1.getId(), pos1);
        DBFunc.movePlot(pos1, pos2);
        TaskManager.runTaskLater(whenDone, 1);
        return true;
    }

    /**
     * Move a plot physically, as well as the corresponding settings.
     * @param origin
     * @param destination
     * @param whenDone
     * @param allowSwap
     * @return
     */
    public static boolean move(final Plot origin, final Plot destination, final Runnable whenDone, boolean allowSwap) {
        PlotId offset = new PlotId(destination.getId().x - origin.getId().x, destination.getId().y - origin.getId().y);
        Location db = destination.getBottomAbs();
        Location ob = origin.getBottomAbs();
        final int offsetX = db.getX() - ob.getX();
        final int offsetZ = db.getZ() - ob.getZ();
        if (origin.owner == null) {
            TaskManager.runTaskLater(whenDone, 1);
            return false;
        }
        boolean occupied = false;
        HashSet<Plot> plots = MainUtil.getConnectedPlots(origin);
        for (Plot plot : plots) {
            Plot other = MainUtil.getPlotAbs(destination.world, new PlotId(plot.getId().x + offset.x, plot.getId().y + offset.y));
            if (other.owner != null) {
                if (!allowSwap) {
                    TaskManager.runTaskLater(whenDone, 1);
                    return false;
                }
                occupied = true;
            }
        }
        // world border
        updateWorldBorder(destination);
        final ArrayDeque<RegionWrapper> regions = new ArrayDeque<>(getRegions(origin));
        // move / swap data
        for (Plot plot : plots) {
            Plot other = MainUtil.getPlotAbs(destination.world, new PlotId(plot.getId().x + offset.x, plot.getId().y + offset.y));
            swapData(plot, other, null);
        }
        // copy terrain
        Runnable move = new Runnable() {
            @Override
            public void run() {
                if (regions.size() == 0) {
                    TaskManager.runTask(whenDone);
                    return;
                }
                final Runnable task = this;
                RegionWrapper region = regions.poll();
                Location[] corners = getCorners(origin.world, region);
                final Location pos1 = corners[0];
                final Location pos2 = corners[1];
                Location newPos = pos1.clone().add(offsetX, 0, offsetZ);
                newPos.setWorld(destination.world);
                ChunkManager.manager.copyRegion(pos1, pos2, newPos, new Runnable() {
                    @Override
                    public void run() {
                        ChunkManager.manager.regenerateRegion(pos1, pos2, task);
                    }
                });
            }
        };
        Runnable swap = new Runnable() {
            @Override
            public void run() {
                if (regions.size() == 0) {
                    TaskManager.runTask(whenDone);
                    return;
                }
                RegionWrapper region = regions.poll();
                Location[] corners = getCorners(origin.world, region);
                Location pos1 = corners[0];
                Location pos2 = corners[1];
                Location pos3 = pos1.clone().add(offsetX, 0, offsetZ);
                Location pos4 = pos2.clone().add(offsetX, 0, offsetZ);
                pos3.setWorld(destination.world);
                pos4.setWorld(destination.world);
                ChunkManager.manager.swap(pos1, pos2, pos3, pos4, this);
            }
        };
        if (occupied) {
            swap.run();
        }
        else {
            move.run();
        }
        return true;
    }
    
    /**
     * Copy a plot to a location, both physically and the settings
     * @param origin
     * @param destination
     * @param whenDone
     * @return
     */
    public static boolean copy(final Plot origin, final Plot destination, final Runnable whenDone) {
        PlotId offset = new PlotId(destination.getId().x - origin.getId().x, destination.getId().y - origin.getId().y);
        Location db = destination.getBottomAbs();
        Location ob = origin.getBottomAbs();
        final int offsetX = db.getX() - ob.getX();
        final int offsetZ = db.getZ() - ob.getZ();
        if (origin.owner == null) {
            TaskManager.runTaskLater(whenDone, 1);
            return false;
        }
        HashSet<Plot> plots = MainUtil.getConnectedPlots(origin);
        for (Plot plot : plots) {
            Plot other = MainUtil.getPlotAbs(destination.world, new PlotId(plot.getId().x + offset.x, plot.getId().y + offset.y));
            if (other.owner != null) {
                TaskManager.runTaskLater(whenDone, 1);
                return false;
            }
        }
        // world border
        updateWorldBorder(destination);
        // copy data
        for (Plot plot : plots) {
            Plot other = MainUtil.getPlotAbs(destination.world , new PlotId(plot.getId().x + offset.x, plot.getId().y + offset.y));
            other = createPlotAbs(plot.owner, other);
            if ((plot.getFlags() != null) && (plot.getFlags().size() > 0)) {
                other.getSettings().flags = plot.getFlags();
                DBFunc.setFlags(other, plot.getFlags().values());
            }
            if (plot.isMerged()) {
                other.setMerged(plot.getMerged());
            }
            if ((plot.members != null) && (plot.members.size() > 0)) {
                other.members = plot.members;
                for (final UUID member : other.members) {
                    DBFunc.setMember(other, member);
                }
            }
            if ((plot.trusted != null) && (plot.trusted.size() > 0)) {
                other.trusted = plot.trusted;
                for (final UUID trusted : other.trusted) {
                    DBFunc.setTrusted(other, trusted);
                }
            }
            if ((plot.denied != null) && (plot.denied.size() > 0)) {
                other.denied = plot.denied;
                for (final UUID denied : other.denied) {
                    DBFunc.setDenied(other, denied);
                }
            }
            PS.get().updatePlot(other);
        }
        // copy terrain
        final ArrayDeque<RegionWrapper> regions = new ArrayDeque<>(getRegions(origin));
        Runnable run = new Runnable() {
            @Override
            public void run() {
                if (regions.size() == 0) {
                    TaskManager.runTask(whenDone);
                    return;
                }
                RegionWrapper region = regions.poll();
                Location[] corners = getCorners(origin.world, region);
                Location pos1 = corners[0];
                Location pos2 = corners[1];
                Location newPos = pos1.clone().add(offsetX, 0, offsetZ);
                newPos.setWorld(destination.world);
                ChunkManager.manager.copyRegion(pos1, pos2, newPos, this);
            }
        };
        run.run();
        return true;
    }
    
    /**
     * Send a message to the player
     *
     * @param plr Player to recieve message
     * @param msg Message to send
     *
     * @return true Can be used in things such as commands (return PlayerFunctions.sendMessage(...))
     */
    public static boolean sendMessage(final PlotPlayer plr, final String msg) {
        return sendMessage(plr, msg, true);
    }
    
    /**
     * Send a message to console
     * @param caption
     * @param args
     */
    public static void sendConsoleMessage(final C caption, final String... args) {
        sendMessage(null, caption, args);
    }
    
    /**
     * Send a message to a player
     * @param plr Can be null to represent console, or use ConsolePlayer.getConsole()
     * @param msg
     * @param prefix If the message should be prefixed with the configured prefix
     * @return
     */
    public static boolean sendMessage(final PlotPlayer plr, final String msg, final boolean prefix) {
        if ((msg.length() > 0) && !msg.equals("")) {
            if (plr == null) {
                ConsolePlayer.getConsole().sendMessage((prefix ? C.PREFIX.s() : "") + msg);
            } else {
                plr.sendMessage((prefix ? C.PREFIX.s() : "") + C.color(msg));
            }
        }
        return true;
    }
    
    /**
     * Send a message to the player
     *
     * @param plr Player to recieve message
     * @param c   Caption to send
     *
     * @return boolean success
     */
    public static boolean sendMessage(final PlotPlayer plr, final C c, final String... args) {
        return sendMessage(plr, c, (Object[]) args);
    }
    
    /**
     * Send a message to the player
     *
     * @param plr Player to recieve message
     * @param c   Caption to send
     *
     * @return boolean success
     */
    public static boolean sendMessage(final PlotPlayer plr, final C c, final Object... args) {
        if (c.s().length() == 0) {
            return true;
        }
        TaskManager.runTaskAsync(new Runnable() {
            @Override
            public void run() {
                String msg = c.s();
                if (args.length != 0) {
                    msg = c.format(c, args);
                }
                if (plr != null) {
                    plr.sendMessage((c.usePrefix() ? C.PREFIX.s() + msg : msg));
                } else {
                    ConsolePlayer.getConsole().sendMessage((c.usePrefix() ? C.PREFIX.s() : "") + msg);
                }
            }
        });
        return true;
    }
    
    /**
     * @deprecated raw access is deprecated
     */
    @Deprecated
    public static HashSet<Plot> connected_cache;
    public static HashSet<RegionWrapper> regions_cache;
    
    public static HashSet<Plot> getConnectedPlots(Plot plot) {
        if (plot == null) {
            return null;
        }
        if (plot.settings == null) {
            return new HashSet<>(Collections.singletonList(plot));
        }
        boolean[] merged = plot.getMerged();
        int hash = hash(merged);
        if (hash == 0) {
            return new HashSet<>(Collections.singletonList(plot));
        }
        if (connected_cache != null && connected_cache.contains(plot)) {
            return connected_cache;
        }
        regions_cache = null;
        connected_cache = new HashSet<Plot>();
        ArrayDeque<Plot> frontier = new ArrayDeque<>();
        HashSet<Object> queuecache = new HashSet<>();
        connected_cache.add(plot);
        Plot tmp;
        if (merged[0]) {
            tmp = getPlotAbs(plot.world, getPlotIdRelative(plot.getId(), 0));
            if (!tmp.getMerged(2)) {
                // invalid merge
                PS.debug("Fixing invalid merge: " + plot);
                if (tmp.hasOwner()) {
                    tmp.getSettings().setMerged(2, true);
                    DBFunc.setMerged(tmp, tmp.settings.getMerged());
                } else {
                    plot.getSettings().setMerged(0, false);
                    DBFunc.setMerged(plot, plot.settings.getMerged());
                }
            }
            queuecache.add(tmp);
            frontier.add(tmp);
        }
        if (merged[1]) {
            tmp = getPlotAbs(plot.world, getPlotIdRelative(plot.getId(), 1));
            if (!tmp.getMerged(3)) {
                // invalid merge
                PS.debug("Fixing invalid merge: " + plot);
                if (tmp.hasOwner()) {
                    tmp.getSettings().setMerged(3, true);
                    DBFunc.setMerged(tmp, tmp.settings.getMerged());
                } else {
                    plot.getSettings().setMerged(1, false);
                    DBFunc.setMerged(plot, plot.settings.getMerged());
                }
            }
            queuecache.add(tmp);
            frontier.add(tmp);
        }
        if (merged[2]) {
            tmp = getPlotAbs(plot.world, getPlotIdRelative(plot.getId(), 2));
            if (!tmp.getMerged(0)) {
                // invalid merge
                PS.debug("Fixing invalid merge: " + plot);
                if (tmp.hasOwner()) {
                    tmp.getSettings().setMerged(0, true);
                    DBFunc.setMerged(tmp, tmp.settings.getMerged());
                } else {
                    plot.getSettings().setMerged(2, false);
                    DBFunc.setMerged(plot, plot.settings.getMerged());
                }
            }
            queuecache.add(tmp);
            frontier.add(tmp);
        }
        if (merged[3]) {
            tmp = getPlotAbs(plot.world, getPlotIdRelative(plot.getId(), 3));
            if (!tmp.getMerged(1)) {
                // invalid merge
                PS.debug("Fixing invalid merge: " + plot);
                if (tmp.hasOwner()) {
                    tmp.getSettings().setMerged(1, true);
                    DBFunc.setMerged(tmp, tmp.settings.getMerged());
                } else {
                    plot.getSettings().setMerged(3, false);
                    DBFunc.setMerged(plot, plot.settings.getMerged());
                }
            }
            queuecache.add(tmp);
            frontier.add(tmp);
        }
        Plot current;
        while ((current = frontier.poll()) != null) {
            if (current.owner == null || current.settings == null) {
                // Invalid plot
                // merged onto unclaimed plot
                PS.debug("Ignoring invalid merged plot: " + current + " | " + current.owner);
                continue;
            }
            connected_cache.add(current);
            queuecache.remove(current);
            merged = current.getMerged();
            if (merged[0]) {
                tmp = getPlotAbs(current.world, getPlotIdRelative(current.getId(), 0));
                if (!queuecache.contains(tmp) && !connected_cache.contains(tmp)) {
                    queuecache.add(tmp);
                    frontier.add(tmp);
                }
            }
            if (merged[1]) {
                tmp = getPlotAbs(current.world, getPlotIdRelative(current.getId(), 1));
                if (!queuecache.contains(tmp) && !connected_cache.contains(tmp)) {
                    queuecache.add(tmp);
                    frontier.add(tmp);
                }
            }
            if (merged[2]) {
                tmp = getPlotAbs(current.world, getPlotIdRelative(current.getId(), 2));
                if (!queuecache.contains(tmp) && !connected_cache.contains(tmp)) {
                    queuecache.add(tmp);
                    frontier.add(tmp);
                }
            }
            if (merged[3]) {
                tmp = getPlotAbs(current.world, getPlotIdRelative(current.getId(), 3));
                if (!queuecache.contains(tmp) && !connected_cache.contains(tmp)) {
                    queuecache.add(tmp);
                    frontier.add(tmp);
                }
            }
        }
        return connected_cache;
    }
    
    /**
     * Fetches the plot from the main class
     */
    public static Plot getPlotAbs(final String world, final PlotId id) {
        if (id == null) {
            return null;
        }
        final Plot plot = PS.get().getPlot(world, id);
        if (plot != null) {
            return plot;
        }
        return new Plot(world, id, null);
    }
    
    /**
     * Gets all the connected plots
     */
    public static HashSet<Plot> getPlots(final String world, final PlotId id) {
        if (id == null) {
            return null;
        }
        final Plot plot = PS.get().getPlot(world, id);
        if (plot != null) {
            return getConnectedPlots(plot);
        }
        return new HashSet<>(Collections.singletonList(new Plot(world, id, null)));
    }
    
    /**
     * Returns the plot id at a location (mega plots are considered)
     * @param loc
     * @return PlotId PlotId observed id
     */
    public static PlotId getPlotId(final Location loc) {
        final String world = loc.getWorld();
        final PlotManager manager = PS.get().getPlotManager(world);
        if (manager == null) {
            return null;
        }
        final PlotWorld plotworld = PS.get().getPlotWorld(world);
        final PlotId id = manager.getPlotId(plotworld, loc.getX(), loc.getY(), loc.getZ());
        if ((id != null) && (plotworld.TYPE == 2)) {
            if (ClusterManager.getCluster(world, id) == null) {
                return null;
            }
        }
        return id;
    }
    
    /**
     * Get the maximum number of plots a player is allowed
     *
     * @param p
     * @return int
     */
    public static int getAllowedPlots(final PlotPlayer p) {
        return Permissions.hasPermissionRange(p, "plots.plot", Settings.MAX_PLOTS);
    }
    
    /**
     * Get the plot at a location
     * @param loc
     * @return The plot
     */
    public static Plot getPlotAbs(final Location loc) {
        final PlotId id = getPlotId(loc);
        if (id == null) {
            return null;
        }
        return getPlotAbs(loc.getWorld(), id);
    }
    
    /**
     * Get the plot and all connected plots at a location
     * @param loc
     * @return A set of plots
     */
    public static Set<Plot> getPlots(final Location loc) {
        final PlotId id = getPlotId(loc);
        if (id == null) {
            return null;
        }
        return getPlots(loc.getWorld(), id);
    }
    
    /**
     * Get the average rating for a plot
     * @see Plot#getAverageRating()
     * @param plot
     * @return
     */
    public static double getAverageRating(final Plot plot) {
        HashMap<UUID, Integer> rating;
        if (plot.getSettings().ratings != null) {
            rating = plot.getSettings().ratings;
        } else if (Settings.CACHE_RATINGS) {
            rating = new HashMap<>();
        } else {
            rating = DBFunc.getRatings(plot);
        }
        if ((rating == null) || (rating.size() == 0)) {
            return 0;
        }
        double val = 0;
        int size = 0;
        for (final Entry<UUID, Integer> entry : rating.entrySet()) {
            int current = entry.getValue();
            if ((Settings.RATING_CATEGORIES == null) || (Settings.RATING_CATEGORIES.size() == 0)) {
                val += current;
                size++;
            } else {
                for (int i = 0; i < Settings.RATING_CATEGORIES.size(); i++) {
                    val += (current % 10) - 1;
                    current /= 10;
                    size++;
                }
            }
        }
        return val / size;
    }
    
    /**
     * If rating categories are enabled, get the average rating by category.<br>
     *  - The index corresponds to the index of the category in the config
     * @param plot
     * @return
     */
    public static double[] getAverageRatings(final Plot plot) {
        HashMap<UUID, Integer> rating;
        if (plot.getSettings().ratings != null) {
            rating = plot.getSettings().ratings;
        } else if (Settings.CACHE_RATINGS) {
            rating = new HashMap<>();
        } else {
            rating = DBFunc.getRatings(plot);
        }
        int size = 1;
        if (Settings.RATING_CATEGORIES != null) {
            size = Math.max(1, Settings.RATING_CATEGORIES.size());
        }
        final double[] ratings = new double[size];
        if ((rating == null) || (rating.size() == 0)) {
            return ratings;
        }
        for (final Entry<UUID, Integer> entry : rating.entrySet()) {
            int current = entry.getValue();
            if ((Settings.RATING_CATEGORIES == null) || (Settings.RATING_CATEGORIES.size() == 0)) {
                ratings[0] += current;
            } else {
                for (int i = 0; i < Settings.RATING_CATEGORIES.size(); i++) {
                    ratings[i] += (current % 10) - 1;
                    current /= 10;
                }
            }
        }
        for (int i = 0; i < size; i++) {
            ratings[i] /= rating.size();
        }
        return ratings;
    }
    
    /**
     * Set a component for a plot to the provided blocks<br>
     *  - E.g. floor, wall, border etc.<br>
     *  - The available components depend on the generator being used<br>
     * @param plot
     * @param component
     * @param blocks
     * @return
     */
    public static boolean setComponent(final Plot plot, final String component, final PlotBlock[] blocks) {
        return plot.getManager().setComponent(plot.getWorld(), plot.getId(), component, blocks);
    }
    
    /**
     * Format a string with plot information:<br>
     * %id%, %alias%, %num%, %desc%, %biome%, %owner%, %members%, %trusted%, %helpers%, %denied%, %flags%, %build%, %desc%, %rating%
     * @param info
     * @param plot
     * @param player
     * @param full
     * @param whenDone
     */
    public static void format(String info, final Plot plot, final PlotPlayer player, final boolean full, final RunnableVal<String> whenDone) {
        final int num = MainUtil.getConnectedPlots(plot).size();
        final String alias = plot.getAlias().length() > 0 ? plot.getAlias() : C.NONE.s();
        final Location bot = plot.getCorners()[0];
        final String biome = BlockManager.manager.getBiome(plot.world, bot.getX(), bot.getZ());
        final String trusted = getPlayerList(plot.getTrusted());
        final String members = getPlayerList(plot.getMembers());
        final String denied = getPlayerList(plot.getDenied());
        
        final Flag descriptionFlag = FlagManager.getPlotFlagRaw(plot, "description");
        final String description = descriptionFlag == null ? C.NONE.s() : descriptionFlag.getValueString();
        
        final String flags = StringMan.replaceFromMap(
        "$2"
        + (StringMan.join(FlagManager.getPlotFlags(plot.world, plot.getSettings(), true).values(), "").length() > 0 ? StringMan.join(FlagManager.getPlotFlags(plot.world, plot.getSettings(), true)
        .values(), "$1, $2") : C.NONE.s()), C.replacements);
        final boolean build = plot.isAdded(player.getUUID());
        
        final String owner = plot.owner == null ? "unowned" : getPlayerList(plot.getOwners());
        
        info = info.replaceAll("%id%", plot.getId().toString());
        info = info.replaceAll("%alias%", alias);
        info = info.replaceAll("%num%", num + "");
        info = info.replaceAll("%desc%", description);
        info = info.replaceAll("%biome%", biome);
        info = info.replaceAll("%owner%", owner);
        info = info.replaceAll("%members%", members);
        info = info.replaceAll("%trusted%", trusted);
        info = info.replaceAll("%helpers%", members);
        info = info.replaceAll("%denied%", denied);
        info = info.replaceAll("%flags%", Matcher.quoteReplacement(flags));
        info = info.replaceAll("%build%", build + "");
        info = info.replaceAll("%desc%", "No description set.");
        if (info.contains("%rating%")) {
            final String newInfo = info;
            TaskManager.runTaskAsync(new Runnable() {
                @Override
                public void run() {
                    int max = 10;
                    if ((Settings.RATING_CATEGORIES != null) && (Settings.RATING_CATEGORIES.size() > 0)) {
                        max = 8;
                    }
                    String info;
                    if (full && (Settings.RATING_CATEGORIES != null) && (Settings.RATING_CATEGORIES.size() > 1)) {
                        String rating = "";
                        String prefix = "";
                        final double[] ratings = MainUtil.getAverageRatings(plot);
                        for (int i = 0; i < ratings.length; i++) {
                            rating += prefix + Settings.RATING_CATEGORIES.get(i) + "=" + String.format("%.1f", ratings[i]);
                            prefix = ",";
                        }
                        info = newInfo.replaceAll("%rating%", rating);
                    } else {
                        info = newInfo.replaceAll("%rating%", String.format("%.1f", MainUtil.getAverageRating(plot)) + "/" + max);
                    }
                    whenDone.run(info);
                }
            });
            return;
        }
        whenDone.run(info);
    }
    
    /**
     * Get a list of names given a list of uuids.<br>
     * - Uses the format {@link C#PLOT_USER_LIST} for the returned string
     * @param uuids
     * @return
     */
    public static String getPlayerList(final Collection<UUID> uuids) {
        final ArrayList<UUID> l = new ArrayList<>(uuids);
        if (l.size() < 1) {
            return C.NONE.s();
        }
        final String c = C.PLOT_USER_LIST.s();
        final StringBuilder list = new StringBuilder();
        for (int x = 0; x < l.size(); x++) {
            if ((x + 1) == l.size()) {
                list.append(c.replace("%user%", getName(l.get(x))).replace(",", ""));
            } else {
                list.append(c.replace("%user%", getName(l.get(x))));
            }
        }
        return list.toString();
    }
}
