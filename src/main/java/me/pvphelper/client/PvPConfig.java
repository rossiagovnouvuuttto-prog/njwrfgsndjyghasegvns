package me.pvphelper.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PvPConfig {
    public boolean aimAssist = true;
    public boolean autoAttack = false;
    public boolean antiBot = true;
    public boolean aimOnlyWhileAttacking = true;
    public boolean ignoreInvisible = true;

    public AttackMode attackMode = AttackMode.NORMAL;
    public AttackSpeed attackSpeed = AttackSpeed.SMART;
    public double range = 3.2;
    public double aimFov = 90.0;
    public double aimStrength = 0.22;

    // Kept only for backward compatibility with old config files.
    public float cooldownThreshold = 0.92f;

    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("pvphelper.properties");

    public enum AttackMode {
        NORMAL,
        CRIT
    }

    public enum AttackSpeed {
        SAFE,
        SMART,
        FAST
    }

    public void load() {
        if (!Files.exists(path)) {
            save();
            return;
        }

        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
            aimAssist = bool(p, "aimAssist", aimAssist);
            autoAttack = bool(p, "autoAttack", autoAttack);
            antiBot = bool(p, "antiBot", antiBot);
            aimOnlyWhileAttacking = bool(p, "aimOnlyWhileAttacking", aimOnlyWhileAttacking);
            ignoreInvisible = bool(p, "ignoreInvisible", ignoreInvisible);
            range = clamp(num(p, "range", range), 2.5, 4.5);
            aimFov = clamp(num(p, "aimFov", aimFov), 20.0, 180.0);
            aimStrength = clamp(num(p, "aimStrength", aimStrength), 0.05, 1.0);
            cooldownThreshold = (float) clamp(num(p, "cooldownThreshold", cooldownThreshold), 0.1, 1.0);

            try {
                attackMode = AttackMode.valueOf(p.getProperty("attackMode", attackMode.name()));
            } catch (IllegalArgumentException ignored) {
                attackMode = AttackMode.NORMAL;
            }

            try {
                attackSpeed = AttackSpeed.valueOf(p.getProperty("attackSpeed", attackSpeed.name()));
            } catch (IllegalArgumentException ignored) {
                attackSpeed = AttackSpeed.SMART;
            }
        } catch (IOException ignored) {
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("aimAssist", Boolean.toString(aimAssist));
        p.setProperty("autoAttack", Boolean.toString(autoAttack));
        p.setProperty("antiBot", Boolean.toString(antiBot));
        p.setProperty("aimOnlyWhileAttacking", Boolean.toString(aimOnlyWhileAttacking));
        p.setProperty("ignoreInvisible", Boolean.toString(ignoreInvisible));
        p.setProperty("attackMode", attackMode.name());
        p.setProperty("attackSpeed", attackSpeed.name());
        p.setProperty("range", Double.toString(range));
        p.setProperty("aimFov", Double.toString(aimFov));
        p.setProperty("aimStrength", Double.toString(aimStrength));
        p.setProperty("cooldownThreshold", Float.toString(cooldownThreshold));

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "PvP Helper 1.16.5 settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(def)));
    }

    private static double num(Properties p, String key, double def) {
        try {
            return Double.parseDouble(p.getProperty(key, Double.toString(def)));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
