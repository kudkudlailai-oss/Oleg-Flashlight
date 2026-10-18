package com.backroomsflashlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/backrooms_flashlight.json (created on first start). Read by the server. */
public final class FlashlightConfig {
    /**
     * Client side: which flashlight you get.
     * "item"           = the craftable flashlight item (hold it, press F), with the lens star and light haze.
     * "found_footage"  = like Backrooms: Found Footage: no item needed, press G to switch the light on/off,
     *                    it shines from your eyes (a wide soft cone with a narrow bright core), no star, no haze.
     */
    public String style = "item";
    /** Client side: how far the beam reaches, in blocks. */
    public float range = 22.0f;
    /** Client side: how bright the beam lights surfaces (1.0 = default, 2.0 = twice as bright). */
    public float strength = 1.0f;
    /** Client side: strength of the light haze in the air (volumetric light). 0 = off, 1 = default, 2 = twice as strong. Both styles. */
    public float volumetric = 1.0f;
    /** Client side: bloom (bright lights glow softly). "off", "low", "medium" or "high". Needs the spotlight effect option on. */
    public String bloom = "medium";
    /** Client side: floor reflections. "off", "low", "medium" or "high". Needs the spotlight effect option on. */
    public String reflections = "medium";
    /** Client side: water (and ice / glass tops) act like a mirror, with ripples. Needs the spotlight effect option on. */
    public boolean water_reflections = true;
    /** Client side: "wet" = floors only mirror while it rains (default), "always" = every smooth grey floor mirrors all the time. */
    public String floor_mode = "wet";
    public boolean spawn_with_flashlight = true;
    public boolean give_on_respawn = true;
    /** Client side: the star-shaped glare on the lens while a flashlight is on. */
    public boolean lens_glare = true;
    /** Client side: real spotlight drawn from the depth buffer (first person, vanilla renderer only, not with shader packs). */
    public boolean spotlight_pass = true;
    /** Client side: depth buffer convention. false = standard depth (26.2, default). true = reverse-Z (26.3). If the light looks wrong, flip it. */
    public boolean reverse_depth = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static FlashlightConfig INSTANCE = new FlashlightConfig();

    /** "low" / "medium" / "high", or null when bloom is off. */
    public static String bloomLevel() {
        String b = INSTANCE.bloom == null ? "" : INSTANCE.bloom.toLowerCase();
        return switch (b) { case "low", "medium", "high" -> b; case "off" -> null; default -> "medium"; };
    }

    /** Reflection strength 0 (off), 0.35, 0.6 or 0.9. */
    public static float reflectionStrength() {
        String b = INSTANCE.reflections == null ? "" : INSTANCE.reflections.toLowerCase();
        return switch (b) { case "off" -> 0f; case "low" -> 0.35f; case "high" -> 0.9f; default -> 0.6f; };
    }

    public static boolean foundFootage() {
        return "found_footage".equalsIgnoreCase(INSTANCE.style);
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("backrooms_flashlight.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    FlashlightConfig c = GSON.fromJson(r, FlashlightConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            save();
        } catch (Exception e) {
            System.err.println("[backrooms_flashlight] could not read/write config: " + e);
        }
    }

    public static void save() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("backrooms_flashlight.json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            System.err.println("[backrooms_flashlight] could not write config: " + e);
        }
    }
}
