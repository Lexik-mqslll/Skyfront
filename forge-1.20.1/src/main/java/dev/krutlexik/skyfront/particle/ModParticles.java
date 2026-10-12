package dev.krutlexik.skyfront.particle;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = 
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SkyfrontMod.MOD_ID);
    
    public static final RegistryObject<SimpleParticleType> DEBRIS =
        PARTICLES.register("debris", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> EXPLOSION_FLASH =
        PARTICLES.register("explosion_flash", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> EMBER =
        PARTICLES.register("ember", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> BLACK_SMOKE =
        PARTICLES.register("black_smoke", () -> new SimpleParticleType(true));

    public static final RegistryObject<SimpleParticleType> SHRAPNEL =
        PARTICLES.register("shrapnel", () -> new SimpleParticleType(true));
}
