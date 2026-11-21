package dev.tony.visuals.stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Plays a vanilla UI sound by name ("ui.toast.challenge_complete"), so we don't depend on how SoundEvents fields are typed. */
final class Sounds {
    private Sounds() {}

    static void play(String id, float pitch, float volume) {
        try {
            SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
            if (e != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
        } catch (Throwable ignored) {
        }
    }
}
