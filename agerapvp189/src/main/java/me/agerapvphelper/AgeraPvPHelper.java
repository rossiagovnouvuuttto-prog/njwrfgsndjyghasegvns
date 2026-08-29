package me.agerapvphelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.ClientCommandHandler;
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
    public static final String VERSION = "1.0.0";

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double RANGE = 3.2D;
    private static final float FOV = 90.0F;
    private static final float AIM_STRENGTH = 0.28F;
    private static final long ATTACK_DELAY_MS = 100L; // 10 CPS

    private static boolean aimEnabled = true;
    private static boolean autoAttackEnabled = false;
    private static long lastAttackMs = 0L;

    private static KeyBinding aimKey;
    private static KeyBinding autoAttackKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        aimKey = new KeyBinding("Toggle Aim Assist", Keyboard.KEY_G, "AgeraPvP Helper");
        autoAttackKey = new KeyBinding("Toggle AutoAttack 10 CPS", Keyboard.KEY_H, "AgeraPvP Helper");
        ClientRegistry.registerKeyBinding(aimKey);
        ClientRegistry.registerKeyBinding(autoAttackKey);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        handleKeys();

        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            lastAttackMs = 0L;
            return;
        }

        EntityPlayer target = findTarget(mc.thePlayer);
        if (target == null) {
            return;
        }

        if (aimEnabled) {
            aimAt(mc.thePlayer, target);
        }

        if (autoAttackEnabled) {
            autoAttack(mc.thePlayer, target);
        }
    }

    private static void handleKeys() {
        while (aimKey != null && aimKey.isPressed()) {
            aimEnabled = !aimEnabled;
            notifyPlayer("Aim Assist: " + (aimEnabled ? "ON" : "OFF"));
        }

        while (autoAttackKey != null && autoAttackKey.isPressed()) {
            autoAttackEnabled = !autoAttackEnabled;
            notifyPlayer("AutoAttack 10 CPS: " + (autoAttackEnabled ? "ON" : "OFF"));
        }
    }

    private static EntityPlayer findTarget(EntityPlayerSP self) {
        EntityPlayer best = null;
        double bestDistance = RANGE;

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }

            EntityPlayer candidate = (EntityPlayer) obj;
            if (candidate == self || candidate.isDead || candidate.getHealth() <= 0.0F) {
                continue;
            }
            if (candidate.isInvisible()) {
                continue;
            }
            if (!self.canEntityBeSeen(candidate)) {
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

        if (self.getDistanceToEntity(target) > RANGE || !self.canEntityBeSeen(target)) {
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
