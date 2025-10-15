package com.tabularasa.addon.scan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class RadiusScanVolume implements ScanVolume {
    private final Box boundingBox;
    private final double radiusSquared;

    public RadiusScanVolume(int radius, int depthAbove, int depthBelow) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d center = client.player.getPos();

        double minX = center.x - radius;
        double maxX = center.x + radius;
        double minY = center.y - depthBelow;
        double maxY = center.y + depthAbove;
        double minZ = center.z - radius;
        double maxZ = center.z + radius;

        boundingBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        radiusSquared = radius * radius;
    }

    @Override
    public Box getBoundingBox() {
        return boundingBox;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        Vec3d center = MinecraftClient.getInstance().player.getPos();
        Vec3d point = Vec3d.ofCenter(pos);
        return center.squaredDistanceTo(point) <= radiusSquared;
    }
}