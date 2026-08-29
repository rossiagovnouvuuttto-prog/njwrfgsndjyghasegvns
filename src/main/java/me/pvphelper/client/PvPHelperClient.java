package me.pvphelper.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PvPHelperClient implements ClientModInitializer {
    public static final PvPConfig CONFIG = new PvPConfig();

    private static KeyBinding menuKey;
    private static KeyBinding aimToggleKey;
    private static KeyBinding autoAttackToggleKey;
    private static KeyBinding attackModeKey;
    private static KeyBinding antiBotToggleKey;

    private static final Map<UUID, Integer> seenTicks = new HashMap<>();
    private static int critJumpTicks;

    @Override
    public void onInitializeClient() {
        CONFIG.load();

        menuKey = register("key.pvphelper.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        aimToggleKey = register("key.pvphelper.toggle_aim", GLFW.GLFW_KEY_G);
        autoAttackToggleKey = register("key.pvphelper.toggle_auto", GLFW.GLFW_KEY_H);
        attackModeKey = register("key.pvphelper.toggle_mode", GLFW.GLFW_KEY_J);
        antiBotToggleKey = register("key.pvphelper.toggle_antibot", GLFW.GLFW_KEY_B);

        ClientTickEvents.END_CLIENT_TICK.register(PvPHelperClient::tick);
    }

    private static KeyBinding register(String key, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                key,
                InputUtil.Type.KEYSYM,
                glfwKey,
                "category.pvphelper"
        ));
    }

    private static void tick(MinecraftClient client) {
        handleKeys(client);

        if (client.player == null || client.world == null || client.interactionManager == null) {
            seenTicks.clear();
            critJumpTicks = 0;
            return;
        }

        updateSeenPlayers(client);

        // Pause combat helpers while eating. They resume automatically afterwards.
        if (isEatingFood(client)) {
            critJumpTicks = 0;
            return;
        }

        PlayerEntity target = findTarget(client);
        if (target == null) {
            critJumpTicks = 0;
            return;
        }

        if (CONFIG.aimAssist && (!CONFIG.aimOnlyWhileAttacking || client.options.keyAttack.isPressed() || CONFIG.autoAttack)) {
            aimAt(client, target);
        }

        if (CONFIG.autoAttack) {
            autoAttack(client, target);
        }
    }

    private static boolean isEatingFood(MinecraftClient client) {
        ItemStack active = client.player.getActiveItem();
        return client.player.isUsingItem()
                && !active.isEmpty()
                && active.getItem().isFood();
    }

    private static void handleKeys(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            client.openScreen(new PvPMenuScreen());
        }

        while (aimToggleKey.wasPressed()) {
            CONFIG.aimAssist = !CONFIG.aimAssist;
            CONFIG.save();
            notify(client, "Aim Assist: " + onOff(CONFIG.aimAssist));
        }

        while (autoAttackToggleKey.wasPressed()) {
            CONFIG.autoAttack = !CONFIG.autoAttack;
            CONFIG.save();
            notify(client, "AutoAttack: " + onOff(CONFIG.autoAttack));
        }

        while (attackModeKey.wasPressed()) {
            CONFIG.attackMode = CONFIG.attackMode == PvPConfig.AttackMode.NORMAL
                    ? PvPConfig.AttackMode.CRIT : PvPConfig.AttackMode.NORMAL;
            CONFIG.save();
            notify(client, "Attack mode: " + (CONFIG.attackMode == PvPConfig.AttackMode.CRIT ? "CRIT" : "NORMAL"));
        }

        while (antiBotToggleKey.wasPressed()) {
            CONFIG.antiBot = !CONFIG.antiBot;
            CONFIG.save();
            notify(client, "AntiBot: " + onOff(CONFIG.antiBot));
        }
    }

    private static void notify(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(new LiteralText("[PvPHelper] " + message), true);
        }
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void updateSeenPlayers(MinecraftClient client) {
        Set<UUID> current = new HashSet<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == client.player) {
                continue;
            }
            UUID uuid = player.getUuid();
            current.add(uuid);

            if (isInTabList(client, player)) {
                int ticks = seenTicks.containsKey(uuid) ? seenTicks.get(uuid) : 0;
                seenTicks.put(uuid, Math.min(400, ticks + 1));
            } else {
                seenTicks.remove(uuid);
            }
        }
        seenTicks.keySet().retainAll(current);
    }

    private static PlayerEntity findTarget(MinecraftClient client) {
        PlayerEntity best = null;
        double bestDistance = CONFIG.range * CONFIG.range;

        for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == client.player || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            if (CONFIG.ignoreInvisible && candidate.isInvisible()) {
                continue;
            }
            if (CONFIG.antiBot && isBot(client, candidate)) {
                continue;
            }

            double dist = client.player.squaredDistanceTo(candidate);
            if (dist > bestDistance) {
                continue;
            }

            float wantedYaw = getYawTo(client.player, candidate);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(wantedYaw - client.player.yaw));
            if (yawDiff > CONFIG.aimFov * 0.5f) {
                continue;
            }

            best = candidate;
            bestDistance = dist;
        }

        return best;
    }

    private static boolean isBot(MinecraftClient client, AbstractClientPlayerEntity candidate) {
        PlayerListEntry entry = getTabEntry(client, candidate);
        if (entry == null) {
            return true;
        }

        Integer ticks = seenTicks.get(candidate.getUuid());
        if (ticks == null || ticks < 10) {
            return true;
        }

        String name = candidate.getGameProfile().getName();
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return true;
        }

        String lower = name.toLowerCase();
        if (lower.contains("[npc]") || lower.startsWith("npc_") || lower.startsWith("bot_")) {
            return true;
        }

        // ReallyWorld heuristic: armor alone NEVER makes a real player a bot.
        // Only treat the armor pattern as suspicious when the entity has just appeared
        // and TAB reports zero latency, which is much closer to an anti-cheat decoy.
        return ticks < 60 && entry.getLatency() <= 0 && hasReallyWorldBotArmor(candidate);
    }

    private static boolean hasReallyWorldBotArmor(PlayerEntity candidate) {
        ItemStack helmet = candidate.inventory.getArmorStack(3);
        ItemStack chest = candidate.inventory.getArmorStack(2);
        ItemStack legs = candidate.inventory.getArmorStack(1);
        ItemStack boots = candidate.inventory.getArmorStack(0);

        return isPlainLeather(helmet, Items.LEATHER_HELMET)
                || isPlainLeather(chest, Items.LEATHER_CHESTPLATE)
                || isPlainLeather(legs, Items.LEATHER_LEGGINGS)
                || isPlainLeather(boots, Items.LEATHER_BOOTS)
                || isUnenchanted(chest, Items.IRON_CHESTPLATE)
                || isUnenchanted(legs, Items.IRON_LEGGINGS);
    }

    private static boolean isPlainLeather(ItemStack stack, Item expectedItem) {
        if (stack.isEmpty() || stack.getItem() != expectedItem || stack.hasEnchantments()) {
            return false;
        }

        Item item = stack.getItem();
        return !(item instanceof DyeableItem) || !((DyeableItem) item).hasColor(stack);
    }

    private static boolean isUnenchanted(ItemStack stack, Item expectedItem) {
        return !stack.isEmpty() && stack.getItem() == expectedItem && !stack.hasEnchantments();
    }

    private static PlayerListEntry getTabEntry(MinecraftClient client, AbstractClientPlayerEntity candidate) {
        if (client.getNetworkHandler() == null) {
            return null;
        }
        return client.getNetworkHandler().getPlayerListEntry(candidate.getUuid());
    }

    private static boolean isInTabList(MinecraftClient client, AbstractClientPlayerEntity candidate) {
        return getTabEntry(client, candidate) != null;
    }

    private static void aimAt(MinecraftClient client, PlayerEntity target) {
        float targetYaw = getYawTo(client.player, target);
        float targetPitch = getPitchTo(client.player, target);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - client.player.yaw);
        float pitchDelta = targetPitch - client.player.pitch;

        float strength = (float) CONFIG.aimStrength;
        client.player.yaw += yawDelta * strength;
        client.player.pitch = MathHelper.clamp(client.player.pitch + pitchDelta * strength, -90.0f, 90.0f);
    }

    private static void autoAttack(MinecraftClient client, PlayerEntity target) {
        if (client.player.getAttackCooldownProgress(0.5f) < CONFIG.cooldownThreshold) {
            return;
        }

        if (CONFIG.attackMode == PvPConfig.AttackMode.CRIT) {
            if (client.player.isOnGround()) {
                client.player.jump();
                critJumpTicks = 1;
                return;
            }

            if (critJumpTicks > 0) {
                critJumpTicks++;
            }

            if (client.player.getVelocity().y >= -0.03) {
                return;
            }
        }

        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
        critJumpTicks = 0;
    }

    private static float getYawTo(PlayerEntity from, PlayerEntity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private static float getPitchTo(PlayerEntity from, PlayerEntity to) {
        double dx = to.getX() - from.getX();
        double dy = to.getEyeY() - from.getEyeY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }
}
