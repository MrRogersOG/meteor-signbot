package com.tabularasa.addon.modules;

import com.tabularasa.addon.helpers.BaritoneHelper;
import com.tabularasa.addon.scan.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * TabulaRasaScanner is a Minecraft module that scans for signs in the game world,
 * logs their content, and can optionally navigate to or destroy them.
 */
public class TabulaRasaScanner extends Module {
    public enum ScanMode {
        RADIUS_SCAN,
        CORRIDOR_SCAN
    }

    // Create a settings group for general configuration
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /**
     * Setting to enable or disable sign scanning functionality
     */
    private final Setting<Boolean> scanSigns = sgGeneral.add(new BoolSetting.Builder()
    .name("Scan Signs")
    .description("Enable scanning for signs.")
    .defaultValue(true)
    .build());

    /**
     * Setting to enable or disable automatic destruction of signs
     */
    private final Setting<Boolean> autoDestroy = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Destroy")
        .description("Automatically destroy signs when close.")
        .defaultValue(false)
        .build());

    /**
     * Setting to enable or disable Baritone pathfinding to signs
     */
    private final Setting<Boolean> useBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("Use Baritone")
        .description("Enable Baritone pathing to scanned signs.")
        .defaultValue(false)
        .build());

    /**
     * Setting to enable or disable debug logging
     */
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("Debug Mode")
        .description("Enable debug logging.")
        .defaultValue(false)
        .build());

    /**
     * Setting to control whether the scan volume is rendered in the world
     */
    private final Setting<Boolean> renderScanVolume = sgGeneral.add(new BoolSetting.Builder()
        .name("Render Scan Volume")
        .description("Show the scan area in world-space.")
        .defaultValue(true)
        .build());

    /**
     * Setting to choose the scanning mode (radius or corridor)
     */
    private final Setting<ScanMode> scanMode = sgGeneral.add(new EnumSetting.Builder<ScanMode>()
        .name("Scan Mode")
        .description("Select scanning strategy.")
        .defaultValue(ScanMode.RADIUS_SCAN)
        .build());

    /**
     * Setting for the radius of the scan area when using radius scan mode
     */
    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("Radius")
        .description("Radius for radius scan.")
        .defaultValue(64)
        .range(8, 256)
        .build());

    /**
     * Setting for the width of the corridor when using corridor scan mode
     */
    private final Setting<Integer> scanWidth = sgGeneral.add(new IntSetting.Builder()
        .name("Corridor Width")
        .description("Width of corridor scan.")
        .defaultValue(3)
        .range(1, 16)
        .build());

    /**
     * Setting for the length of the corridor when using corridor scan mode
     */
    private final Setting<Integer> scanLength = sgGeneral.add(new IntSetting.Builder()
        .name("Corridor Length")
        .description("Length of corridor scan.")
        .defaultValue(32)
        .range(8, 128)
        .build());

    /**
     * Setting for how many blocks above the player to scan
     */
    private final Setting<Integer> depthAbove = sgGeneral.add(new IntSetting.Builder()
        .name("Depth Above")
        .description("Blocks above player to scan.")
        .defaultValue(2)
        .range(0, 16)
        .build());

    /**
     * Setting for how many blocks below the player to scan
     */
    private final Setting<Integer> depthBelow = sgGeneral.add(new IntSetting.Builder()
        .name("Depth Below")
        .description("Blocks below player to scan.")
        .defaultValue(4)
        .range(0, 16)
        .build());

    // Private final field to store the MinecraftClient instance
    // This is a singleton instance obtained from MinecraftClient.getInstance()
    private final MinecraftClient client = MinecraftClient.getInstance();
    
    /**
     * A list that stores positions of signs that need to be processed
     * The list is declared as final to ensure its reference cannot be changed
     */
    private final List<BlockPos> signQueue = new ArrayList<>();
    
    // File path for the log file
    // The log file is named "tabularasa-log.txt"
    private final String logFilePath = "tabularasa-log.txt";

    
    // Private variable to store the timer value for scanning operations
    private int scanTimer = 0;

    /**
     * Constructor for TabulaRasaScanner
     * Initializes the module with name, category, and description
     */
    public TabulaRasaScanner() {
        super("TabulaRasa", Categories.Misc, "Scans signs and logs historical data.");
    }

    /**
     * Called when the module is activated
     * Resets the scan timer and sends activation message
     */
    @Override
    public void onActivate() {
        scanTimer = 0; // Reset timer on activation
        sendChat("[TabulaRasa] Module activated.");
    }

    /**
     * Called when the module is deactivated
     * Clears the sign queue and cancels any ongoing paths
     */
    @Override
    public void onDeactivate() {
        signQueue.clear();
        BaritoneHelper.cancelPath();
        sendChat("[TabulaRasa] Module deactivated.");
    }

    /**
     * Called on each game tick
     * Handles sign scanning, auto destruction, and pathfinding
     */
    @Override
    public void onTick() {
        // Check if sign scanning is enabled
        if (scanSigns.get()) {
            // Perform scan when timer reaches 0
            if (scanTimer <= 0) {
                scanSigns();
                scanTimer = 100; // Rescan every 5 seconds (20 ticks per second)
            } else {
                scanTimer--;
            }
        }

        // Handle auto destruction if enabled
        if (autoDestroy.get() && !signQueue.isEmpty() && client.player != null) {
            BlockPos target = signQueue.get(0);
            double distance = client.player.getPos().distanceTo(Vec3d.ofCenter(target));
            // Destroy sign if within range
            if (distance < 4.5) {
                client.interactionManager.attackBlock(target, client.player.getHorizontalFacing());
                log("[DESTROYED] Sign at " + target.toShortString());
                signQueue.remove(0);
            }
        }

        
        // Check if Baritone is enabled, not currently pathing, and there are signs in the queue
        if (useBaritone.get() && !BaritoneHelper.isPathing() && !signQueue.isEmpty()) {
            // Get the next position from the queue
            BlockPos next = signQueue.remove(0);
            // Send Baritone to the next position
            BaritoneHelper.goTo(next);
            log("[DISPATCHED] Baritone sent to " + next.toShortString());
        }
    }

    /**
     * Handles 3D rendering events
     * Renders the scan volume if enabled
     */
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        // Skip rendering if disabled or player is null
        if (!renderScanVolume.get() || client.player == null) return;

        // Create appropriate scan volume based on mode
        ScanVolume volume;
        if (scanMode.get() == ScanMode.RADIUS_SCAN) {
            volume = new RadiusScanVolume(radius.get(), depthAbove.get(), depthBelow.get());
        } else {
            volume = new CorridorScanVolume(scanWidth.get(), depthAbove.get(), depthBelow.get(), scanLength.get());
        }

        // Set rendering color and render the volume
        Color color = new Color(0, 255, 255, 50); // Light blue translucent
        ScanVolumeRenderer.render(event, volume, color);
    }

    /**
     * Scans for signs in the defined volume
     * Queues valid signs and logs the results
     */
    private void scanSigns() {
        // Return if world or player is null
        if (client.world == null || client.player == null) return;

        // Create appropriate scan volume based on mode
        ScanVolume volume;
        if (scanMode.get() == ScanMode.RADIUS_SCAN) {
            volume = new RadiusScanVolume(radius.get(), depthAbove.get(), depthBelow.get());
        } else {
            volume = new CorridorScanVolume(scanWidth.get(), depthAbove.get(), depthBelow.get(), scanLength.get());
        }

        // Counters for statistics
        int total = 0, blank = 0, valid = 0;
        signQueue.clear();

        // Iterate through all block entities in the world
        for (BlockEntity entity : client.world.getBlockEntities()) {
            // Check if entity is a sign
            if (entity instanceof SignBlockEntity sign) {
                BlockPos pos = sign.getPos();
                // Check if sign is within scan volume
                if (volume.isInside(pos)) {
                    // Get all text lines from the sign
                    String[] lines = new String[4];
                    for (int i = 0; i < 4; i++) {
                        lines[i] = sign.getText(true).getMessage(i, false).getString().trim();
                    }

                    // Check if sign is blank
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

        // Send scan results to chat
        sendChat("[TabulaRasa] Scan complete: " + total + " signs, " + valid + " valid, " + blank + " blank.");
    }

    /**
     * Saves sign data to a TSV file
     * @param pos The block position of the sign
     * @param lines An array containing the four lines of text on the sign
     */
    private void saveSign(BlockPos pos, String[] lines) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("signs.tsv", true))) { // Create buffered writer to append to signs.tsv file
            writer.write(pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ() + "\t" +
                         lines[0] + "\t" + lines[1] + "\t" + lines[2] + "\t" + lines[3]);
            writer.newLine();
        } catch (IOException e) {
            log("[ERROR] Failed to save sign: " + e.getMessage());
        }
    }

    /**
     * Logs a message to the log file if debug mode is enabled
     * @param message The message to be logged
     */
    private void log(String message) {
        // Check if debug mode is enabled, if not, return immediately
        if (!debugMode.get()) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            // Write the message to the log file
            writer.write(message);
            // Add a new line after the message
            writer.newLine();
        } catch (IOException e) {
            // Print stack trace if an IOException occurs
            e.printStackTrace();
        }
    }

    /**
     * Sends a chat message in the game
     * @param message The message to be sent
     */
    private void sendChat(String message) {
        client.inGameHud.getChatHud().addMessage(Text.of(message));
    }
}

// Verify that all methods annotated with @Override exist in the superclass or interface.
// Ensure imports are correct and resolve missing classes.
// Ensure ScanMode enum is correctly defined and used.
// Replace direct access to blockEntities with an appropriate getter method or API.
// Fix private access to blockEntities in TabulaRasaScanner.java