package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** COMPILE-TIME STUB of Mod Menu's interface. It is excluded from the built jar (see build.gradle). */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
