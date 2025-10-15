package com.tabularasa.addon.modules;

import com.tabularasa.addon.helpers.BaritoneHelper;
import com.tabularasa.addon.scan.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Module.Category;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TabulaRasaScanner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> scanSigns = sgGeneral.add(new BoolSetting.Builder()
        .name("Scan Signs")
        .description("Enable scanning for signs.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoDestroy = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Destroy")
        .description("Automatically destroy signs when close.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> useBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("Use Baritone")
        .description("Enable Baritone pathing to scanned signs.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Enable debug logging.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> renderScanVolume = sgGeneral.add(new BoolSetting.Builder()
        .name("Render Scan Volume")
        .description("Show the scan area in world-space.")
        .defaultValue(true)
        .build());

    private final Setting<ScanMode> scanMode = sgGeneral.add(new EnumSetting.Builder<ScanMode>()
        .name("Scan Mode")
        .description("Select scanning strategy.")
        .defaultValue(ScanMode.RADIUS_SCAN)
        .build());

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("Radius")
        .description("Radius for radius scan.")
        .defaultValue(64)
        .range(8, 256)
        .build());

    private final Setting<Integer> scanWidth = sgGeneral.add(new IntSetting.Builder()
        .name("Corridor Width")
        .description("Width of corridor scan.")
        .defaultValue(3)
        .range(1, 16)
        .build());

    private final Setting<Integer> scanLength = sgGeneral.add(new IntSetting.Builder()
        .name("Corridor Length")
        .description("Length of corridor scan.")
        .defaultValue(32)
        .range(8, 128)
        .build());

    private final Setting<Integer> depthAbove = sgGeneral.add(new IntSetting.Builder()
        .name("Depth Above")
        .description("Blocks above player to scan.")
        .defaultValue(2)
        .range(0, 16)
        .build());

    private final Setting<Integer> depthBelow = sgGeneral.add(new IntSetting.Builder()
        .name("Depth Below")
        .description("Blocks below player to scan.")
        .defaultValue(4)
        .range(0, 16)
        .build());

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<BlockPos> signQueue = new ArrayList<>();
    private final String logFilePath = "tabularasa-log.txt";

    private int scanTimer = 0;

    public TabulaRasaScanner() {
        super("TabulaRasa", Category.MISC, "Scans signs and logs historical data.");
    }

    @Override
    public void onActivate() {
        scanTimer = 0; // Reset timer on activation
        sendChat("[TabulaRasa] Module activated.");
    }

    @Override
    public void onDeactivate() {
        signQueue.clear();
        BaritoneHelper.cancelPath();
        sendChat("[TabulaRasa] Module deactivated.");
    }

    @Override
    public void onTick() {
        if (scanSigns.get()) {
            if (scanTimer <= 0) {
                scanSigns();
                scanTimer = 100; // Rescan every 5 seconds (20 ticks per second)
            } else {
                scanTimer--;
            }
        }

        if (autoDestroy.get() && !signQueue.isEmpty() && client.player != null) {
            BlockPos target = signQueue.get(0);
            double distance = client.player.getPos().distanceTo(Vec3d.ofCenter(target));
            if (distance < 4.5) {
                client.interactionManager.attackBlock(target, client.player.getHorizontalFacing());
                log("[DESTROYED] Sign at " + target.toShortString());
                signQueue.remove(0);
            }
        }

        if (useBaritone.get() && !BaritoneHelper.isPathing() && !signQueue.isEmpty()) {
            BlockPos next = signQueue.remove(0);
            BaritoneHelper.goTo(next);
            log("[DISPATCHED] Baritone sent to " + next.toShortString());
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderScanVolume.get() || client.player == null) return;

        ScanVolume volume;
        if (scanMode.get() == ScanMode.RADIUS_SCAN) {
            volume = new RadiusScanVolume(radius.get(), depthAbove.get(), depthBelow.get());
        } else {
            volume = new CorridorScanVolume(scanWidth.get(), depthAbove.get(), depthBelow.get(), scanLength.get());
        }

        Color color = new Color(0, 255, 255, 50); // Light blue translucent
        ScanVolumeRenderer.render(event, volume, color);
    }

    private void scanSigns() {
        if (client.world == null || client.player == null) return;

        ScanVolume volume;
        if (scanMode.get() == ScanMode.RADIUS_SCAN) {
            volume = new RadiusScanVolume(radius.get(), depthAbove.get(), depthBelow.get());
        } else {
            volume = new CorridorScanVolume(scanWidth.get(), depthAbove.get(), depthBelow.get(), scanLength.get());
        }

        int total = 0, blank = 0, valid = 0;
        signQueue.clear();

        for (BlockEntity entity : client.world.blockEntities) {
            if (entity instanceof SignBlockEntity sign) {
                BlockPos pos = sign.getPos();
                if (volume.isInside(pos)) {
                    String[] lines = new String[4];
                    for (int i = 0; i < 4; i++) {
                        lines[i] = sign.getText(true).getMessage(i, false).getString().trim();
                    }

                    boolean isBlank = lines[0].isEmpty() && lines[1].isEmpty() && lines[2].isEmpty() && lines[3].isEmpty();
                    total++;

                    if (isBlank) {
                        blank++;
                        log("[SKIPPED] Blank sign at " + pos.toShortString());
                    } else {
                        valid++;
                        signQueue.add(pos);
                        saveSign(pos, lines);
                        log("[QUEUED] Valid sign at " + pos.toShortString());
                    }
                }
            }
        }

        sendChat("[TabulaRasa] Scan complete: " + total + " signs, " + valid + " valid, " + blank + " blank.");
    }

    private void saveSign(BlockPos pos, String[] lines) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("signs.tsv", true))) {
            writer.write(pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ() + "\t" +
                         lines[0] + "\t" + lines[1] + "\t" + lines[2] + "\t" + lines[3]);
            writer.newLine();
        } catch (IOException e) {
            log("[ERROR] Failed to save sign: " + e.getMessage());
        }
    }

    private void log(String message) {
        if (!debugMode.get()) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendChat(String message) {
        client.inGameHud.getChatHud().addMessage(Text.of(message));
    }
}