package com.utilitymod.echo.utils.player;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Frame-accurate velocity estimator using EMA smoothing.
 * get() returns blocks/tick, getPerSecond() returns blocks/second.
 */
public final class CustomGetVelocity {
	private CustomGetVelocity() {}

	private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();
	private static final double SMOOTH_TAU_SEC = 0.08;
	private static final double MIN_DT_SEC = 0.001;
	private static final double MAX_DT_SEC = 0.25;
	private static final double TELEPORT_THRESHOLD_BLOCKS = 8.0;

	private static ClientWorld lastWorldRef = null;

	private static final class State {
		Vec3d lastPos;
		long lastNs;
		Vec3d velPerSecEma;
		int samples;

		State(Vec3d pos, long nowNs) {
			this.lastPos = pos;
			this.lastNs = nowNs;
			this.velPerSecEma = Vec3d.ZERO;
			this.samples = 0;
		}
	}

	public static void updateAll() {
		final MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) return;

		if (lastWorldRef != mc.world) {
			STATES.clear();
			lastWorldRef = mc.world;
		}

		for (PlayerEntity p : mc.world.getPlayers()) {
			updateFor(p);
		}
	}

	public static void updateFor(Entity e) {
		Objects.requireNonNull(e, LT.L1);
		final long nowNs = System.nanoTime();
		final Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());

		State s = STATES.get(e.getId());
		if (s == null) {
			s = new State(pos, nowNs);
			STATES.put(e.getId(), s);
			return;
		}

		double dt = (nowNs - s.lastNs) / 1_000_000_000.0;
		if (dt <= MIN_DT_SEC) return;
		
		if (dt > MAX_DT_SEC) {
			s.lastPos = pos;
			s.lastNs = nowNs;
			return;
		}

		Vec3d dp = pos.subtract(s.lastPos);
		if (dp.length() > TELEPORT_THRESHOLD_BLOCKS) {
			s.lastPos = pos;
			s.lastNs = nowNs;
			s.velPerSecEma = Vec3d.ZERO;
			s.samples = 0;
			return;
		}

		Vec3d vPerSec = dp.multiply(1.0 / dt);
		double alpha = emaAlpha(dt, SMOOTH_TAU_SEC);
		
		if (s.samples == 0) {
			s.velPerSecEma = vPerSec;
		} else {
			s.velPerSecEma = lerp(s.velPerSecEma, vPerSec, alpha);
		}
		s.samples++;

		s.lastPos = pos;
		s.lastNs = nowNs;
	}

	public static Vec3d get(Entity e) {
		return getPerSecond(e).multiply(1.0 / 20.0);
	}

	public static Vec3d getPerSecond(Entity e) {
		State s = STATES.get(e.getId());
		if (s != null && s.samples > 0) {
			return s.velPerSecEma;
		}
		return e.getVelocity().multiply(20.0);
	}

	public static double getHorizontalSpeed(Entity e) {
		Vec3d v = get(e);
		return Math.hypot(v.x, v.z);
	}

	public static double getHorizontalSpeedPerSecond(Entity e) {
		Vec3d v = getPerSecond(e);
		return Math.hypot(v.x, v.z);
	}

	public static void reset(Entity e) {
		STATES.remove(e.getId());
	}

	public static void resetAll() {
		STATES.clear();
		lastWorldRef = null;
	}

	public static boolean hasSample(Entity e) {
		State s = STATES.get(e.getId());
		return s != null && s.samples > 0;
	}

	private static double emaAlpha(double dt, double tau) {
		if (tau <= 0) return 1.0;
		double a = 1.0 - Math.exp(-dt / tau);
		return Math.max(0.0, Math.min(1.0, a));
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		return new Vec3d(
			a.x + (b.x - a.x) * t,
			a.y + (b.y - a.y) * t,
			a.z + (b.z - a.z) * t
		);
	}


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x402b,0xeedc,0xb1a7,0x52af,0xe338,0x952e}, new char[]{0x404e,0xe356,0xb1d3,0xeeb5,0x955a,0x52d6}).plainString();
    }
}
