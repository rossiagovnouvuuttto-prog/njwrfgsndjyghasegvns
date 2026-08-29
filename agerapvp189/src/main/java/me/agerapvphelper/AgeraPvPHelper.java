package me.agerapvphelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(
        modid = AgeraPvPHelper.MODID,
        name = AgeraPvPHelper.NAME,
        version = AgeraPvPHelper.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]"
)
public final class AgeraPvPHelper {
    public static final String MODID = "agerapvphelper";
    public static final String NAME = "AgeraPvP Helper";
    public static final String VERSION = "1.1.1";

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double RANGE = 3.2D;
    private static final float FOV = 90.0F;
    private static final float AIM_STRENGTH = 0.28F;
    private static final long ATTACK_DELAY_MS = 100L; // max 10 CPS

    private static boolean aimEnabled = true;
    private static boolean autoAttackEnabled = false;
    private static boolean bridgeAssistEnabled = false;
    private static boolean bridgeSneakOwned = false;
    private static long lastAttackMs = 0L;

    private static KeyBinding aimKey;
    private static KeyBinding autoAttackKey;
    private static KeyBinding bridgeKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        aimKey = new KeyBinding("Toggle Aim Assist", Keyboard.KEY_G, "AgeraPvP Helper");
        autoAttackKey = new KeyBinding("Toggle AutoAttack 10 CPS", Keyboard.KEY_H, "AgeraPvP Helper");
        bridgeKey = new KeyBinding("Toggle Bridge Assist", Keyboard.KEY_B, "AgeraPvP Helper");
        ClientRegistry.registerKeyBinding(aimKey);
        ClientRegistry.registerKeyBinding(autoAttackKey);
        ClientRegistry.registerKeyBinding(bridgeKey);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        handleKeys();

        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            releaseBridgeSneak();
            lastAttackMs = 0L;
            return;
        }

        EntityPlayerSP self = mc.thePlayer;
        boolean attackHeld = mc.gameSettings.keyBindAttack.isKeyDown();
        boolean bridgingWithBlocks = bridgeAssistEnabled && isHoldingBlock(self);

        if (bridgingWithBlocks && !attackHeld) {
            updateEdgeGuard(self);
        } else {
            releaseBridgeSneak();
        }

        // While actually bridging, combat helpers stay out of the way.
        // Holding LMB always gives combat priority, even if Bridge Assist is ON and a block is held.
        if (bridgingWithBlocks && !attackHeld) {
            lastAttackMs = 0L;
            return;
        }

        EntityPlayer target = getCombatTarget(self);
        if (target == null) {
            lastAttackMs = 0L;
            return;
        }

        if (aimEnabled && attackHeld) {
            aimAt(self, target);
        }

        // v1.1.1: AutoAttack is hold-to-attack. H enables it, then holding LMB produces up to 10 CPS.
        if (autoAttackEnabled && attackHeld) {
            autoAttack(self, target);
        } else if (!attackHeld) {
            lastAttackMs = 0L;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null) {
            return;
        }

        if (!bridgeAssistEnabled) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        String status;
        int color;

        if (!isHoldingBlock(mc.thePlayer)) {
            status = "BRIDGE: HOLD BLOCKS";
            color = 0xAAAAAA;
        } else if (mc.gameSettings.keyBindAttack.isKeyDown()) {
            status = "COMBAT PRIORITY";
            color = 0xFFAA55;
        } else if (canPlaceAtCrosshair()) {
            status = "PLACE NOW";
            color = 0x55FF55;
        } else if (isNearEdge(mc.thePlayer)) {
            status = "EDGE GUARD";
            color = 0xFFFF55;
        } else {
            status = "BRIDGE READY";
            color = 0x55FFFF;
        }

        int x = resolution.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(status) / 2;
        int y = resolution.getScaledHeight() / 2 + 28;
        mc.fontRendererObj.drawStringWithShadow(status, x, y, color);
    }

    private static void handleKeys() {
        while (aimKey != null && aimKey.isPressed()) {
            aimEnabled = !aimEnabled;
            notifyPlayer("Aim Assist: " + (aimEnabled ? "ON" : "OFF"));
        }

        while (autoAttackKey != null && autoAttackKey.isPressed()) {
            autoAttackEnabled = !autoAttackEnabled;
            lastAttackMs = 0L;
            notifyPlayer("AutoAttack 10 CPS: " + (autoAttackEnabled ? "ON - HOLD LMB" : "OFF"));
        }

        while (bridgeKey != null && bridgeKey.isPressed()) {
            bridgeAssistEnabled = !bridgeAssistEnabled;
            if (!bridgeAssistEnabled) {
                releaseBridgeSneak();
            }
            notifyPlayer("Bridge Assist: " + (bridgeAssistEnabled ? "ON" : "OFF"));
        }
    }

    private static boolean isHoldingBlock(EntityPlayerSP player) {
        ItemStack stack = player.getHeldItem();
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
    }

    private static void updateEdgeGuard(EntityPlayerSP player) {
        if (!player.onGround || !isNearEdge(player)) {
            releaseBridgeSneak();
            return;
        }

        int sneakCode = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean physicallySneaking = isPhysicalKeyDown(sneakCode);
        if (!physicallySneaking) {
            KeyBinding.setKeyBindState(sneakCode, true);
            bridgeSneakOwned = true;
        }
    }

    private static void releaseBridgeSneak() {
        if (!bridgeSneakOwned || mc.gameSettings == null) {
            return;
        }

        int sneakCode = mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(sneakCode, isPhysicalKeyDown(sneakCode));
        bridgeSneakOwned = false;
    }

    private static boolean isPhysicalKeyDown(int keyCode) {
        return keyCode > 0 && Keyboard.isKeyDown(keyCode);
    }

    private static boolean isNearEdge(EntityPlayerSP player) {
        if (mc.theWorld == null || player.movementInput == null) {
            return false;
        }

        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 0.05D) {
            return false;
        }

        forward /= length;
        strafe /= length;

        double yaw = Math.toRadians(player.rotationYaw);
        double dirX = (-Math.sin(yaw) * forward) + (Math.cos(yaw) * strafe);
        double dirZ = ( Math.cos(yaw) * forward) + (Math.sin(yaw) * strafe);

        double feetY = player.getEntityBoundingBox().minY - 0.08D;
        BlockPos currentBelow = new BlockPos(player.posX, feetY, player.posZ);
        BlockPos aheadBelow = new BlockPos(
                player.posX + dirX * 0.62D,
                feetY,
                player.posZ + dirZ * 0.62D
        );

        return mc.theWorld.isAirBlock(currentBelow) || mc.theWorld.isAirBlock(aheadBelow);
    }

    private static boolean canPlaceAtCrosshair() {
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private static EntityPlayer getCombatTarget(EntityPlayerSP self) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entity = hit.entityHit;
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (isValidTarget(self, player) && self.getDistanceToEntity(player) <= RANGE) {
                    return player;
                }
            }
        }
        return findTarget(self);
    }

    private static boolean isValidTarget(EntityPlayerSP self, EntityPlayer candidate) {
        return candidate != self
                && !candidate.isDead
                && candidate.getHealth() > 0.0F
                && !candidate.isInvisible()
                && self.canEntityBeSeen(candidate);
    }

    private static EntityPlayer findTarget(EntityPlayerSP self) {
        EntityPlayer best = null;
        double bestDistance = RANGE;

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }

            EntityPlayer candidate = (EntityPlayer) obj;
            if (!isValidTarget(self, candidate)) {
                continue;
            }

            double distance = self.getDistanceToEntity(candidate);
            if (distance > RANGE || distance > bestDistance) {
                continue;
            }

            float desiredYaw = getYawTo(self, candidate);
            float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(desiredYaw - self.rotationYaw));
            if (yawDiff > FOV * 0.5F) {
                continue;
            }

            best = candidate;
            bestDistance = distance;
        }

        return best;
    }

    private static void aimAt(EntityPlayerSP self, EntityPlayer target) {
        float desiredYaw = getYawTo(self, target);
        float desiredPitch = getPitchTo(self, target);

        float yawDelta = MathHelper.wrapAngleTo180_float(desiredYaw - self.rotationYaw);
        float pitchDelta = desiredPitch - self.rotationPitch;

        self.rotationYaw += yawDelta * AIM_STRENGTH;
        self.rotationPitch = MathHelper.clamp_float(
                self.rotationPitch + pitchDelta * AIM_STRENGTH,
                -90.0F,
                90.0F
        );
    }

    private static void autoAttack(EntityPlayerSP self, EntityPlayer target) {
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < ATTACK_DELAY_MS) {
            return;
        }

        if (!isValidTarget(self, target) || self.getDistanceToEntity(target) > RANGE) {
            return;
        }

        mc.playerController.attackEntity(self, target);
        self.swingItem();
        lastAttackMs = now;
    }

    private static float getYawTo(EntityPlayer from, EntityPlayer to) {
        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private static float getPitchTo(EntityPlayer from, EntityPlayer to) {
        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        double dy = (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight());
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private static void notifyPlayer(String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[AgeraPvP Helper] " + text));
        }
    }
}
