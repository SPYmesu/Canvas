package io.canvasmc.canvas.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-wide keyed obfuscation of world-generation seeds.
 *
 * <p>When a secret key is supplied through the {@code CANVAS_WORLDGEN_SEED_KEY} environment variable (or the
 * {@code canvas.worldgen.seed-key} system property), every seed the world generator derives from the level seed is
 * folded through a keyed, non-linear mix before it reaches the underlying PRNG state. This breaks the fixed algebraic
 * relationship between the level seed and observable world features that tools such as SeedCrackerX exploit: recovering
 * the internal PRNG state no longer reveals the level seed, and vanilla seeds no longer reproduce this server's worlds.
 *
 * <p>The mix is applied at the four points every world-gen seed funnels through:
 * {@link net.minecraft.world.level.levelgen.RandomSupport#upgradeSeedTo128bit(long)} (all Xoroshiro-rooted noise,
 * biome and structure RNG), and the {@code setSeed} of {@link net.minecraft.world.level.levelgen.LegacyRandomSource},
 * {@link net.minecraft.world.level.levelgen.SingleThreadedRandomSource} and
 * {@link net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource} (legacy worldgen, decoration/structure
 * placement and slime chunks). Positional factories inherit the key from their already-keyed root seed.
 *
 * <p>With no key configured {@link #obfuscate(long)} is the identity function, so existing worlds keep generating
 * byte-for-byte identically. The key derives the world deterministically: it must stay constant for a world to remain
 * stable, exactly like the level seed itself.
 */
@NullMarked
public final class WorldSeedObfuscator {

    private static final Logger LOGGER = LoggerFactory.getLogger("CanvasMC");
    private static final String ENV_KEY = "CANVAS_WORLDGEN_SEED_KEY";
    private static final String PROPERTY_KEY = "canvas.worldgen.seed-key";

    public static final boolean ENABLED;
    private static final long PEPPER_A;
    private static final long PEPPER_B;

    static {
        String key = System.getenv(ENV_KEY);
        if (key == null || key.isEmpty()) {
            key = System.getProperty(PROPERTY_KEY);
        }

        if (key == null || key.isEmpty()) {
            ENABLED = false;
            PEPPER_A = 0L;
            PEPPER_B = 0L;
        } else {
            byte[] digest = sha256(key);
            ENABLED = true;
            PEPPER_A = bytesToLong(digest, 0);
            PEPPER_B = bytesToLong(digest, 8);
            LOGGER.info("World-generation seed obfuscation is ENABLED. Vanilla seeds and seed-cracking tools will not "
                + "match this server's generation. Keep the configured key constant or worlds will regenerate differently.");
        }
    }

    private WorldSeedObfuscator() {
    }

    /**
     * Folds the configured secret key into a world-generation seed. Returns {@code seed} unchanged when no key is set.
     *
     * <p>The transform is a keyed splitmix64 finalizer: a bijection (no seed-space collisions) with full avalanche, so
     * the truncated low bits an attacker can recover from PRNG output still depend on every bit of the secret key.
     */
    public static long obfuscate(final long seed) {
        if (!ENABLED) {
            return seed;
        }
        long z = seed ^ PEPPER_A;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z ^ PEPPER_B;
    }

    private static byte[] sha256(final String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for world seed obfuscation but is unavailable", e);
        }
    }

    private static long bytesToLong(final byte[] bytes, final int offset) {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }
}
