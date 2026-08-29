package me.pvphelper.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Locale;

public final class PvPMenuScreen extends Screen {
    private static final Text TITLE = new LiteralText("PvP Helper 1.16.5 v1.3");

    public PvPMenuScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int w = 220;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = 28;
        int gap = 21;

        addButton(new ButtonWidget(x, y, w, h, textAim(), b -> {
            PvPHelperClient.CONFIG.aimAssist = !PvPHelperClient.CONFIG.aimAssist;
            b.setMessage(textAim());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textAuto(), b -> {
            PvPHelperClient.CONFIG.autoAttack = !PvPHelperClient.CONFIG.autoAttack;
            b.setMessage(textAuto());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textAttackSpeed(), b -> {
            PvPConfig.AttackSpeed speed = PvPHelperClient.CONFIG.attackSpeed;
            if (speed == PvPConfig.AttackSpeed.SMART) {
                speed = PvPConfig.AttackSpeed.FAST;
            } else if (speed == PvPConfig.AttackSpeed.FAST) {
                speed = PvPConfig.AttackSpeed.SAFE;
            } else {
                speed = PvPConfig.AttackSpeed.SMART;
            }
            PvPHelperClient.CONFIG.attackSpeed = speed;
            b.setMessage(textAttackSpeed());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textAntiBot(), b -> {
            PvPHelperClient.CONFIG.antiBot = !PvPHelperClient.CONFIG.antiBot;
            b.setMessage(textAntiBot());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textMode(), b -> {
            PvPHelperClient.CONFIG.attackMode = PvPHelperClient.CONFIG.attackMode == PvPConfig.AttackMode.NORMAL
                    ? PvPConfig.AttackMode.CRIT : PvPConfig.AttackMode.NORMAL;
            b.setMessage(textMode());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textAimActivation(), b -> {
            PvPHelperClient.CONFIG.aimOnlyWhileAttacking = !PvPHelperClient.CONFIG.aimOnlyWhileAttacking;
            b.setMessage(textAimActivation());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textRange(), b -> {
            double r = PvPHelperClient.CONFIG.range;
            if (r < 3.0) r = 3.0;
            else if (r < 3.5) r = 3.5;
            else if (r < 4.0) r = 4.0;
            else if (r < 4.5) r = 4.5;
            else r = 2.8;
            PvPHelperClient.CONFIG.range = r;
            b.setMessage(textRange());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textFov(), b -> {
            double f = PvPHelperClient.CONFIG.aimFov;
            if (f < 45) f = 45;
            else if (f < 90) f = 90;
            else if (f < 140) f = 140;
            else if (f < 180) f = 180;
            else f = 30;
            PvPHelperClient.CONFIG.aimFov = f;
            b.setMessage(textFov());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, textStrength(), b -> {
            double s = PvPHelperClient.CONFIG.aimStrength;
            if (s < 0.15) s = 0.15;
            else if (s < 0.25) s = 0.25;
            else if (s < 0.40) s = 0.40;
            else if (s < 0.65) s = 0.65;
            else s = 0.10;
            PvPHelperClient.CONFIG.aimStrength = s;
            b.setMessage(textStrength());
            save();
        }));

        y += gap;
        addButton(new ButtonWidget(x, y, w, h, new LiteralText("Готово"), b -> onClose()));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, TITLE, width / 2, 7, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText("RShift меню | G Aim | H Auto | J Mode | B AntiBot"), width / 2, 18, 0xA0A0A0);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        save();
        if (client != null) {
            client.openScreen(null);
        }
    }

    private static Text textAim() {
        return new LiteralText("Aim Assist: " + onOff(PvPHelperClient.CONFIG.aimAssist));
    }

    private static Text textAuto() {
        return new LiteralText("Авто-удары: " + onOff(PvPHelperClient.CONFIG.autoAttack));
    }

    private static Text textAttackSpeed() {
        String value;
        if (PvPHelperClient.CONFIG.attackSpeed == PvPConfig.AttackSpeed.FAST) {
            value = "FAST";
        } else if (PvPHelperClient.CONFIG.attackSpeed == PvPConfig.AttackSpeed.SAFE) {
            value = "SAFE";
        } else {
            value = "SMART";
        }
        return new LiteralText("Скорость AutoAttack: " + value);
    }

    private static Text textAntiBot() {
        return new LiteralText("AntiBot: " + onOff(PvPHelperClient.CONFIG.antiBot));
    }

    private static Text textMode() {
        return new LiteralText("Режим ударов: " + (PvPHelperClient.CONFIG.attackMode == PvPConfig.AttackMode.CRIT ? "КРИТЫ" : "ОБЫЧНЫЕ"));
    }

    private static Text textAimActivation() {
        return new LiteralText("Aim только при атаке: " + onOff(PvPHelperClient.CONFIG.aimOnlyWhileAttacking));
    }

    private static Text textRange() {
        return new LiteralText(String.format(Locale.ROOT, "Дистанция: %.1f", PvPHelperClient.CONFIG.range));
    }

    private static Text textFov() {
        return new LiteralText(String.format(Locale.ROOT, "Aim FOV: %.0f°", PvPHelperClient.CONFIG.aimFov));
    }

    private static Text textStrength() {
        return new LiteralText(String.format(Locale.ROOT, "Скорость Aim: %.0f%%", PvPHelperClient.CONFIG.aimStrength * 100.0));
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void save() {
        PvPHelperClient.CONFIG.save();
    }
}
