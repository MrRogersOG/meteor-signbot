package com.tabularasa.addon.scan;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public interface ScanVolume {
    Box getBoundingBox();
    boolean isInside(BlockPos pos);
}