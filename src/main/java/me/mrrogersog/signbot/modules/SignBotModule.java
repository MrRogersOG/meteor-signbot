package me.mrrogersog.signbot.modules;

import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SignBotModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoDispatch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-dispatch")
        .description("Automatically dispatch Baritone #goto commands.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> dispatchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("dispatch-delay")
        .description("Delay between dispatches in ticks (20 ticks = 1 second).")
        .defaultValue(40)
        .range(5, 200)
        .build()
    );

    private final Setting<Keybind> skipKeybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("skip-sign")
        .description("Skip the current sign and dispatch the next one.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final List<BlockPos> signQueue = new ArrayList<>();
    private BlockPos currentTarget = null;
    private int tickCounter = 0;

    public SignBotModule() {
        super(Category.Misc, "SignBot", "Scans signs and sends Baritone #goto commands.");
    }

    @Override
    public void onActivate() {
        scanSigns();
        ChatUtils.info("[SignBot] Scanned and queued valid signs.");
        logToFile("Scanned and queued valid signs.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (skipKeybind.get().isPressed() && !signQueue.isEmpty()) {
            mc.player.sendChatMessage("#stop");
            BlockPos next = signQueue.remove(0);
            currentTarget = next;
            String command = String.format("#goto %d %d %d", next.getX(), next.getY(), next.getZ());
            mc.player.sendChatMessage(command);
            ChatUtils.info("[SignBot] Skipped to next sign: " + command);
            logToFile("Skipped to next sign: " + command);
            tickCounter = 0;
            return;
        }

        if (!autoDispatch.get() || signQueue.isEmpty()) return;

        tickCounter++;
        if (tickCounter >= dispatchDelay.get()) {
            BlockPos target = signQueue.remove(0);
            currentTarget = target;
            String command = String.format("#goto %d %d %d", target.getX(), target.getY(), target.getZ());
            mc.player.sendChatMessage(command);
            ChatUtils.info("[SignBot] Dispatched: " + command);
            logToFile("Dispatched: " + command);
            tickCounter = 0;
        }

        if (currentTarget != null && mc.player.getBlockPos().isWithinDistance(currentTarget, 1.5)) {
            mc.interactionManager.attackBlock(currentTarget, mc.player.getHorizontalFacing());
            ChatUtils.info("[SignBot] Punched sign at: " + currentTarget.toShortString());
            logToFile("Punched sign at: " + currentTarget.toShortString());
            currentTarget = null;
        }
    }

    private void scanSigns() {
        if (mc.world == null || mc.player == null) return;

        signQueue.clear();
        int total = 0, ignored = 0, blank = 0, valid = 0;

        for (BlockEntity entity : mc.world.blockEntities) {
            if (entity instanceof SignBlockEntity sign) {
                total++;
                String line1 = sign.getTextOnRow(0, false).getString().trim();
                String line2 = sign.getTextOnRow(1, false).getString().trim();
                String line3 = sign.getTextOnRow(2, false).getString().trim();
                String line4 = sign.getTextOnRow(3, false).getString().trim();

                boolean isBlank = line1.isEmpty() && line2.isEmpty() && line3.isEmpty() && line4.isEmpty();
                boolean isCody = line1.equals("codysmile11") && line2.startsWith("was here:)");

                BlockPos pos = sign.getPos();

                if (isBlank) {
                    blank++;
                } else if (isCody) {
                    ignored++;
                } else {
                    valid++;
                    signQueue.add(pos);
                }
            }
        }

        ChatUtils.info("[SignBot] Scan complete: " + total + " signs detected");
        ChatUtils.info("[SignBot] " + ignored + " codysmile11 signs ignored 😎");
        ChatUtils.info("[SignBot] " + blank + " blank signs skipped");
        ChatUtils.info("[SignBot] " + valid + " valid signs added to queue");
        ChatUtils.info("[SignBot] Finished scan ✅");

        logToFile("Scan complete: " + total + " signs detected");
        logToFile(ignored + " codysmile11 signs ignored");
        logToFile(blank + " blank signs skipped");
        logToFile(valid + " valid signs added to queue");
        logToFile("Finished scan ✅");
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        Chunk chunk = event.chunk();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++) {
            for (int y = chunk.getBottomY(); y <= chunk.getHighestSectionPosition().getY(); y++) {
                for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.getBlock() instanceof AbstractSignBlock) {
                        BlockEntity be = chunk.getBlockEntity(pos);
                        if (be instanceof SignBlockEntity sign) {
                            String line1 = sign.getTextOnRow(0, false).getString().trim();
                            String line2 = sign.getTextOnRow(1, false).getString().trim();
                            String line3 = sign.getTextOnRow(2, false).getString().trim();
                            String line4 = sign.getTextOnRow(3, false).getString().trim();

                            boolean isBlank = line1.isEmpty() && line2.isEmpty() && line3.isEmpty() && line4.isEmpty();
                            boolean isCody = line1.equals("codysmile11") && line2.startsWith("was here:)");

                            BlockPos signPos = sign.getPos();

                            if (!isBlank && !isCody && !signQueue.contains(signPos)) {
                                signQueue.add(signPos);
                                ChatUtils.info("[SignBot] Queued sign from chunk: " + signPos.toShortString());
                                logToFile("Queued sign from chunk: " + signPos.toShortString());
                            }
                        }
                    }
                }
            }
        }
    }

    private void logToFile(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("logs/signbot-log.txt", true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + message);
            writer.newLine();
        } catch (IOException e) {
            ChatUtils.error("[SignBot] Failed to write to log file.");
        }
    }
}
