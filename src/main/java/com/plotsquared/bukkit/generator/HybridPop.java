package com.plotsquared.bukkit.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.bukkit.World;
import org.bukkit.block.Biome;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.generator.HybridPlotWorld;
import com.intellectualcrafters.plot.object.PlotLoc;
import com.intellectualcrafters.plot.object.PlotWorld;
import com.intellectualcrafters.plot.object.PseudoRandom;
import com.intellectualcrafters.plot.object.RegionWrapper;
import com.intellectualcrafters.plot.object.schematic.PlotItem;
import com.intellectualcrafters.plot.util.BlockManager;

/**
 */
public class HybridPop extends BukkitPlotPopulator {
    /*
     * Sorry, this isn't well documented at the moment.
     * We advise you to take a look at a world generation tutorial for
     * information about how a BlockPopulator works.
     */
    final short plotsize;
    final short pathsize;
    final byte wall;
    final byte wallfilling;
    final byte roadblock;
    final int size;
    final int roadheight;
    final int wallheight;
    final int plotheight;
    final byte[] plotfloors;
    final byte[] filling;
    final short pathWidthLower;
    final short pathWidthUpper;
    private final HybridPlotWorld plotworld;
    Biome biome;
    private boolean doFilling = false;
    private boolean doFloor = false;
    private boolean cached;
    
    public HybridPop(final PlotWorld pw) {
        plotworld = (HybridPlotWorld) pw;
        // save configuration
        plotsize = (short) plotworld.PLOT_WIDTH;
        pathsize = (short) plotworld.ROAD_WIDTH;
        roadblock = plotworld.ROAD_BLOCK.data;
        wallfilling = plotworld.WALL_FILLING.data;
        size = pathsize + plotsize;
        wall = plotworld.WALL_BLOCK.data;
        int count1 = 0;
        int count2 = 0;
        plotfloors = new byte[plotworld.TOP_BLOCK.length];
        for (int i = 0; i < plotworld.TOP_BLOCK.length; i++) {
            count1++;
            plotfloors[i] = plotworld.TOP_BLOCK[i].data;
            if (plotworld.TOP_BLOCK[i].data != 0) {
                doFloor = true;
            }
        }
        filling = new byte[plotworld.MAIN_BLOCK.length];
        for (int i = 0; i < plotworld.MAIN_BLOCK.length; i++) {
            count2++;
            filling[i] = plotworld.MAIN_BLOCK[i].data;
            if (plotworld.MAIN_BLOCK[i].data != 0) {
                doFilling = true;
            }
        }
        if (((count1 > 0) && doFloor) || ((count2 > 0) && doFilling)) {}
        wallheight = plotworld.WALL_HEIGHT;
        roadheight = plotworld.ROAD_HEIGHT;
        plotheight = plotworld.PLOT_HEIGHT;
        if (pathsize == 0) {
            pathWidthLower = (short) -1;
            pathWidthUpper = (short) (plotsize + 1);
        } else {
            if ((pathsize % 2) == 0) {
                pathWidthLower = (short) (Math.floor(pathsize / 2) - 1);
            } else {
                pathWidthLower = (short) (Math.floor(pathsize / 2));
            }
            pathWidthUpper = (short) (pathWidthLower + plotsize + 1);
        }
        
        if (!this.plotworld.PLOT_SCHEMATIC) {
            this.cached = true;
        }
    }
    
    @Override
    public void populate(final World world, final RegionWrapper requiredRegion, final PseudoRandom random, final int cx, final int cz) {
        PS.get().getPlotManager(world.getName());
        int sx = (short) ((X - plotworld.ROAD_OFFSET_X) % size);
        int sz = (short) ((Z - plotworld.ROAD_OFFSET_Z) % size);
        if (sx < 0) {
            sx += size;
        }
        if (sz < 0) {
            sz += size;
        }
        
        if (cached) {
            if ((sx > pathWidthLower) && (sz > pathWidthLower) && ((sx + 15) < pathWidthUpper) && ((sz + 15) < pathWidthUpper)) {
                random.state = 7919;
            }
        }

        if (requiredRegion != null) {
            if (!doFloor && !doFilling && plotworld.G_SCH_DATA == null) {
                return;
            }
            for (short x = 0; x < 16; x++) {
                for (short z = 0; z < 16; z++) {
                    if (contains(requiredRegion, x, z)) {
                        setBlock(x, (short) plotheight, z, plotfloors);
                        if (doFilling) {
                            for (short y = 1; y < plotheight; y++) {
                                setBlock(x, y, z, filling);
                            }
                        }
                        if (plotworld.PLOT_SCHEMATIC) {
                            final int absX = ((sx + x) % size);
                            final int absZ = ((sz + z) % size);
                            final PlotLoc loc = new PlotLoc(absX, absZ);
                            final HashMap<Short, Byte> blocks = plotworld.G_SCH_DATA.get(loc);
                            if (blocks != null) {
                                for (Entry<Short, Byte> entry : blocks.entrySet()) {
                                    setBlockAbs(x, (short) (plotheight + entry.getKey()), z, entry.getValue());
                                }
                            }
                            if (plotworld.G_SCH_STATE != null) {
                                final HashSet<PlotItem> states = plotworld.G_SCH_STATE.get(loc);
                                if (states != null) {
                                    for (final PlotItem items : states) {
                                        BlockManager.manager.addItems(plotworld.worldname, items);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return;
        }
        
        for (short x = 0; x < 16; x++) {
            final int absX = ((sx + x) % size);
            final boolean gx = absX > pathWidthLower;
            final boolean lx = absX < pathWidthUpper;
            for (short z = 0; z < 16; z++) {
                final int absZ = ((sz + z) % size);
                final boolean gz = absZ > pathWidthLower;
                final boolean lz = absZ < pathWidthUpper;
                // inside plot
                if (gx && gz && lx && lz) {
                    setBlock(x, (short) plotheight, z, plotfloors);
                    for (short y = 1; y < plotheight; y++) {
                        setBlock(x, y, z, filling);
                    }
                    if (plotworld.PLOT_SCHEMATIC) {
                        final PlotLoc loc = new PlotLoc(absX, absZ);
                        final HashMap<Short, Byte> blocks = plotworld.G_SCH_DATA.get(loc);
                        if (blocks != null) {
                            for (Entry<Short, Byte> entry : blocks.entrySet()) {
                                setBlockAbs(x, (short) (plotheight + entry.getKey()), z, entry.getValue());
                            }
                        }
                        if (plotworld.G_SCH_STATE != null) {
                            final HashSet<PlotItem> states = plotworld.G_SCH_STATE.get(loc);
                            if (states != null) {
                                for (final PlotItem items : states) {
                                    items.x = X + x;
                                    items.z = Z + z;
                                    BlockManager.manager.addItems(plotworld.worldname, items);
                                }
                            }
                        }
                    }
                } else if (pathsize != 0) {
                    // wall
                    if (((absX >= pathWidthLower) && (absX <= pathWidthUpper) && (absZ >= pathWidthLower) && (absZ <= pathWidthUpper))) {
                        for (short y = 1; y <= wallheight; y++) {
                            setBlock(x, y, z, wallfilling);
                        }
                        if (!plotworld.ROAD_SCHEMATIC_ENABLED) {
                            setBlock(x, wallheight + 1, z, wall);
                        }
                    }
                    // road
                    else {
                        for (short y = 1; y <= roadheight; y++) {
                            setBlock(x, y, z, roadblock);
                        }
                    }
                    if (plotworld.ROAD_SCHEMATIC_ENABLED) {
                        final PlotLoc loc = new PlotLoc(absX, absZ);
                        final HashMap<Short, Byte> blocks = plotworld.G_SCH_DATA.get(loc);
                        if (blocks != null) {
                            for (Entry<Short, Byte> entry : blocks.entrySet()) {
                                setBlockAbs(x, (short) (roadheight + entry.getKey()), z, entry.getValue());
                            }
                        }
                    }
                }
            }
        }
    }
}
