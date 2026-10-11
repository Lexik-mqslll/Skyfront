package dev.krutlexik.skyfront.sound;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, SkyfrontMod.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED_ENGINE = registerSoundEvent("shahed_engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_1 = registerSoundEvent("explosion_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_2 = registerSoundEvent("explosion_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_3 = registerSoundEvent("explosion_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_4 = registerSoundEvent("explosion_4");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_5 = registerSoundEvent("explosion_5");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = registerSoundEvent("siren");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
