package com.tabularasa.addon.scan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class CorridorScanVolume implements ScanVolume {
    private final Box boundingBox;

    public CorridorScanVolume(int width, int depthAbove, int depthBelow, int length) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d playerPos = client.player.getPos();
        Vec3d forward = client.player.getRotationVec(1.0F).normalize();

        Vec3d start = playerPos;
        Vec3d end = playerPos.add(forward.multiply(length));

        double minX = Math.min(start.x, end.x) - width;
        double maxX = Math.max(start.x, end.x) + width;
        double minY = playerPos.y - depthBelow;
        double maxY = playerPos.y + depthAbove;
        double minZ = Math.min(start.z, end.z) - width;
        double maxZ = Math.max(start.z, end.z) + width;

        boundingBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public Box getBoundingBox() {
        return boundingBox;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return boundingBox.contains(Vec3d.ofCenter(pos));
    }
}