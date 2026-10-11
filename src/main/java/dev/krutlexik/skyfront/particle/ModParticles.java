package dev.krutlexik.skyfront.particle;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = 
        DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, SkyfrontMod.MOD_ID);
    
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEBRIS =
        PARTICLES.register("debris", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPLOSION_FLASH =
        PARTICLES.register("explosion_flash", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER =
        PARTICLES.register("ember", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLACK_SMOKE =
        PARTICLES.register("black_smoke", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHRAPNEL =
        PARTICLES.register("shrapnel", () -> new SimpleParticleType(true));
}
