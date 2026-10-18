package com.backroomsflashlight.client;

import com.backroomsflashlight.FlashlightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game settings (opened from Mod Menu). Changes apply right away and are saved when you close the screen.
 * Server-side options (spawn with flashlight, give on respawn) only matter in singleplayer / on the host.
 */
public class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Oleg Flashlight"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FlashlightConfig c = FlashlightConfig.INSTANCE;
        int w = 240;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 138;
        int step = 26;

        addRenderableWidget(Button.builder(Component.literal("Oleg Flashlight settings"), b -> {}).bounds(x, y - 30, w, 20).build()).active = false;

        addRenderableWidget(Button.builder(styleText(), b -> {
            c.style = FlashlightConfig.foundFootage() ? "item" : "found_footage";
            b.setMessage(styleText());
        }).bounds(x, y, w, 20).build());

        addRenderableWidget(Button.builder(rangeText(), b -> {
            c.range = nextRange(c.range);
            b.setMessage(rangeText());
        }).bounds(x, y + step, w, 20).build());

        addRenderableWidget(Button.builder(strengthText(), b -> {
            c.strength = nextStrength(c.strength);
            b.setMessage(strengthText());
        }).bounds(x, y + step * 2, w, 20).build());

        addRenderableWidget(Button.builder(hazeText(), b -> {
            c.volumetric = next(HAZES, c.volumetric);
            if (c.volumetric > 3.0f) c.volumetric = 0f;
            b.setMessage(hazeText());
        }).bounds(x, y + step * 3, w, 20).build());

        addRenderableWidget(Button.builder(onOff("Lens glare (item style)", c.lens_glare), b -> {
            c.lens_glare = !c.lens_glare;
            b.setMessage(onOff("Lens glare (item style)", c.lens_glare));
        }).bounds(x, y + step * 4, w, 20).build());

        addRenderableWidget(Button.builder(onOff("Spotlight effect", c.spotlight_pass), b -> {
            c.spotlight_pass = !c.spotlight_pass;
            b.setMessage(onOff("Spotlight effect", c.spotlight_pass));
        }).bounds(x, y + step * 5, w, 20).build());

        addRenderableWidget(Button.builder(bloomText(), b -> {
            String cur = c.bloom == null ? "medium" : c.bloom.toLowerCase();
            c.bloom = switch (cur) { case "off" -> "low"; case "low" -> "medium"; case "medium" -> "high"; default -> "off"; };
            b.setMessage(bloomText());
        }).bounds(x, y + step * 6, w, 20).build());

        addRenderableWidget(Button.builder(reflText(), b -> {
            String cur = c.reflections == null ? "medium" : c.reflections.toLowerCase();
            c.reflections = switch (cur) { case "off" -> "low"; case "low" -> "medium"; case "medium" -> "high"; default -> "off"; };
            b.setMessage(reflText());
        }).bounds(x, y + step * 7, w, 20).build());

        addRenderableWidget(Button.builder(floorModeText(), b -> {
            c.floor_mode = "always".equalsIgnoreCase(c.floor_mode) ? "wet" : "always";
            b.setMessage(floorModeText());
        }).bounds(x, y + step * 8, w, 20).build());

        addRenderableWidget(Button.builder(onOff("Water reflections", c.water_reflections), b -> {
            c.water_reflections = !c.water_reflections;
            b.setMessage(onOff("Water reflections", c.water_reflections));
        }).bounds(x, y + step * 9, w, 20).build());

        addRenderableWidget(Button.builder(onOff("Spawn with flashlight", c.spawn_with_flashlight), b -> {
            c.spawn_with_flashlight = !c.spawn_with_flashlight;
            b.setMessage(onOff("Spawn with flashlight", c.spawn_with_flashlight));
        }).bounds(x, y + step * 10, w, 20).build());

        addRenderableWidget(Button.builder(onOff("Give again on respawn", c.give_on_respawn), b -> {
            c.give_on_respawn = !c.give_on_respawn;
            b.setMessage(onOff("Give again on respawn", c.give_on_respawn));
        }).bounds(x, y + step * 11, w, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x, y + step * 12 + 8, w, 20).build());
    }

    private static Component bloomText() {
        String b = FlashlightConfig.INSTANCE.bloom == null ? "medium" : FlashlightConfig.INSTANCE.bloom.toLowerCase();
        return Component.literal("Bloom (glow): " + b);
    }

    private static Component floorModeText() {
        return Component.literal("Floor mirror: " + ("always".equalsIgnoreCase(FlashlightConfig.INSTANCE.floor_mode) ? "always" : "only when raining"));
    }

    private static Component reflText() {
        String b = FlashlightConfig.INSTANCE.reflections == null ? "medium" : FlashlightConfig.INSTANCE.reflections.toLowerCase();
        return Component.literal("Floor reflections: " + b);
    }

    private static Component styleText() {
        return Component.literal("Style: " + (FlashlightConfig.foundFootage()
                ? "Found Footage (no item, key G)" : "Item (hold it, key F)"));
    }

    private static final float[] RANGES = {10f, 15f, 22f, 30f, 40f, 60f};
    private static final float[] STRENGTHS = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f};

    private static float next(float[] steps, float cur) {
        for (float s : steps) if (s > cur + 0.001f) return s;
        return steps[0];
    }

    private static final float[] HAZES = {0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 99f};

    private static Component hazeText() {
        float v = FlashlightConfig.INSTANCE.volumetric;
        return Component.literal("Volumetric light: " + (v <= 0.001f ? "OFF" : String.format("%.2f", v) + "x"));
    }

    private static float nextRange(float cur) { return next(RANGES, cur); }
    private static float nextStrength(float cur) { return next(STRENGTHS, cur); }

    private static Component rangeText() {
        return Component.literal("Range: " + Math.round(FlashlightConfig.INSTANCE.range) + " blocks");
    }

    private static Component strengthText() {
        return Component.literal("Strength: " + String.format("%.2f", FlashlightConfig.INSTANCE.strength) + "x");
    }

    private static Component onOff(String label, boolean v) {
        return Component.literal(label + ": " + (v ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        FlashlightConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
