package dev.krutlexik.skyfront.drone.registry;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, DroneMod.MODID);

    /**
     * The Mavics' propeller loop, heard out to 32 blocks: a small Mavic's whine carries a little way,
     * nothing like an interceptor's.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_LOOP =
            SOUNDS.register("drone_loop", () -> SoundEvent.createFixedRangeEvent(DroneMod.id("drone_loop"), 32.0F));

    /** The interceptor's motor loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> INTERCEPTOR_LOOP =
            SOUNDS.register("interceptor_loop",
                    () -> SoundEvent.createFixedRangeEvent(DroneMod.id("interceptor_loop"), 192.0F));

    /**
     * The FPV quad's motors: the interceptor's own loop raised 1.35 times in pitch with its formants kept (so
     * it whines higher without turning into a cartoon), given a little body and grit, and laid out as a
     * ten-second loop whose note drifts slowly so the repeat is not heard.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> FPV_LOOP =
            SOUNDS.register("fpv_loop", () -> SoundEvent.createFixedRangeEvent(DroneMod.id("fpv_loop"), 192.0F));

    /**
     * The Molniya's motor: the interceptor's loop again, a little lower (0.85 of its pitch) and rougher —
     * more body underneath and some grit driven into it — for one bigger motor on a plane rather than four
     * small ones on a quad.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOLNIYA_LOOP =
            SOUNDS.register("molniya_loop", () -> SoundEvent.createFixedRangeEvent(DroneMod.id("molniya_loop"), 192.0F));

    private DroneSounds() {
    }
}
