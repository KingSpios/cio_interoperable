package com.cio.createinteroperable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A direct clone of vanilla's own {@code CampfireSmokeParticle} physics (same
 * scale, size, lifetime range, gravity and fade-out), the one difference
 * being the sprite set it's bound to — see {@link CIOParticles} for why a
 * plain color tint on the vanilla particle couldn't get a white result.
 */
public class RadiatorSmokeParticle extends TextureSheetParticle {
    private RadiatorSmokeParticle(ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z);
        this.scale(3.0F);
        this.setSize(0.25F, 0.25F);
        this.lifetime = this.random.nextInt(50) + 80;
        this.gravity = 3.0E-6F;
        this.xd = xSpeed;
        this.yd = ySpeed + (double) (this.random.nextFloat() / 500.0F);
        this.zd = zSpeed;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && !(this.alpha <= 0.0F)) {
            this.xd += (double) (this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1));
            this.zd += (double) (this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1));
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);
            if (this.age >= this.lifetime - 60 && this.alpha > 0.01F) {
                this.alpha -= 0.015F;
            }
        } else {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed) {
            RadiatorSmokeParticle particle = new RadiatorSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.setAlpha(0.9F);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
