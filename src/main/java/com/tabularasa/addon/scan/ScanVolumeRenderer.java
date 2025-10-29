package com.tabularasa.addon.scan;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ScanVolumeRenderer {
    public static void render(Render3DEvent event, ScanVolume volume, Color color) {
        Box box = volume.getBoundingBox();
        event.renderer.box(
            box,
            color,
            color,
            ShapeMode.Both,
            0
        );
    }
}