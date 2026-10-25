package com.utilitymod.echo.utils.rotation;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Humanized silent-rotation motor (mochi HumanizedAimController port): exponential-velocity
 * steps over wrapped yaw / clamped pitch, eye-point low-pass with optional velocity lead,
 * octave noise drift, fine-grid quantize release. Math is 1:1 with the decompiled source.
 */
public final class HumanizedRotator {
	private final Random random = new Random();
	private final FractalNoise noiseYaw = new FractalNoise();
	private final FractalNoise noisePitch = new FractalNoise();

	private double maxSpeed = 360.0;
	private double followWindow = 17.5;
	private double noiseAmp = 0.0;
	private double lead = 0.0;
	private double confidence = 1.0;

	private boolean eyeSeen;
	private double eyeX, eyeY, eyeZ;
	private double rawX, rawY, rawZ;
	private long lastEyeMs;
	private int eyeSamples;
	private double velX, velY, velZ;
	private boolean driftStarted;
	private double burstYaw, burstPitch;
	private double octaveYaw, octavePitch, sineFreq, sinePhase;
	private double velYaw, velPitch;
	private double carryYaw, carryPitch;

	public HumanizedRotator() {
		reseed();
	}

	public void configure(double maxSpeedDeg, double noiseAmplitude, double velocityLead) {
		this.maxSpeed = Math.max(1.0, maxSpeedDeg);
		if (Math.abs(noiseAmplitude - this.noiseAmp) > 1.0E-6) {
			this.noiseAmp = Math.max(0.0, noiseAmplitude);
			reseed();
		}
		this.followWindow = 5.0 + 25.0 * 0.5;
		this.lead = MathHelper.clamp(velocityLead, 0.0, 1.0);
	}

	public void reset() {
		velYaw = velPitch = 0.0;
		velX = velY = velZ = 0.0;
		carryYaw = carryPitch = 0.0;
		burstYaw = burstPitch = 0.0;
		eyeSeen = false;
		eyeSamples = 0;
		driftStarted = false;
		confidence = 1.0;
	}

	/** One motor step; returns {yaw, pitch} the look should be at this tick. */
	public float[] step(float curYaw, float curPitch, Vec3d from, Vec3d target, long nowMs, double dtSec, boolean snap) {
		if (target == null) {
			if (snap) return RotationUtils.calculate(from, target);
			return new float[]{curYaw, curPitch};
		}
		Vec3d driven = driveTarget(target, nowMs);
		// dt unit law: mochi feeds the tick delta in milliseconds (min 0.05), then scales 1/60;
		// the caller here sends seconds, so convert before clamping or the motor never moves.
		double dt = Math.max(dtSec * 1000.0, 0.05) / 60.0;

		double amp = 0.012 + 0.02 * noiseAmp;
		confidence = MathHelper.clamp(confidence + random.nextGaussian() * amp * Math.sqrt(dt), 0.9, 1.1);

		float[] goal = RotationUtils.calculate(from, driven);

		double[] drift = computeDrift(nowMs, dt, goal[0] - curYaw, goal[1] - curPitch);
		double goalYaw = goal[0] + drift[0];
		double goalPitch = MathHelper.clamp(goal[1] + drift[1], -90.0, 90.0);

		double outYaw = motor(curYaw, goalYaw, true, dt);
		double outPitch = motor(curPitch, goalPitch, false, dt);

		return new float[]{
				(float)(curYaw + outYaw),
				(float)MathHelper.clamp(curPitch + outPitch, -90f, 90f)};
	}

	private Vec3d driveTarget(Vec3d target, long nowMs) {
		double dt = (nowMs - lastEyeMs) / 1000.0;
		if (!eyeSeen) {
			eyeX = rawX = target.x;
			eyeY = rawY = target.y;
			eyeZ = rawZ = target.z;
			velX = velY = velZ = 0.0;
			eyeSamples = 1;
			lastEyeMs = nowMs;
			eyeSeen = true;
			return new Vec3d(eyeX, eyeY, eyeZ);
		}
		if (!(dt <= 0.5)) {
			eyeX = rawX = target.x;
			eyeY = rawY = target.y;
			eyeZ = rawZ = target.z;
			velX = velY = velZ = 0.0;
			eyeSamples = 1;
			lastEyeMs = nowMs;
			return new Vec3d(eyeX, eyeY, eyeZ);
		}
		double dx = target.x - rawX, dy = target.y - rawY, dz = target.z - rawZ;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist > 4.0) {
			eyeX = rawX = target.x;
			eyeY = rawY = target.y;
			eyeZ = rawZ = target.z;
			velX = velY = velZ = 0.0;
			eyeSamples = 1;
			lastEyeMs = nowMs;
			return new Vec3d(eyeX, eyeY, eyeZ);
		}
		if (dt > 1.0E-4) {
			double e = 1.0 - Math.exp(-dt / 0.04);
			eyeX += (target.x - eyeX) * e;
			eyeY += (target.y - eyeY) * e;
			eyeZ += (target.z - eyeZ) * e;
			double k = eyeSamples <= 1 ? 1.0 : 0.28;
			velX += ((target.x - rawX) / dt - velX) * k;
			velY += ((target.y - rawY) / dt - velY) * k;
			velZ += ((target.z - rawZ) / dt - velZ) * k;
			eyeSamples++;
		}
		rawX = target.x;
		rawY = target.y;
		rawZ = target.z;
		lastEyeMs = nowMs;

		if (lead > 0.0 && eyeSamples >= 3) {
			double v = Math.sqrt(velX * velX + velY * velY + velZ * velZ);
			if (v >= 0.35) {
				double scale = 0.08 * lead;
				double ax = velX * scale;
				double ay = velY * scale * 0.5;
				double az = velZ * scale;
				double len = Math.sqrt(ax * ax + ay * ay + az * az);
				if (len > 1.0) {
					double inv = 1.0 / len;
					ax *= inv;
					ay *= inv;
					az *= inv;
				}
				return new Vec3d(eyeX + ax, eyeY + ay, eyeZ + az);
			}
		}
		return new Vec3d(eyeX, eyeY, eyeZ);
	}

	private double[] computeDrift(long nowMs, double dt, double dYaw, double dPitch) {
		if (noiseAmp <= 0.0) {
			burstYaw = burstPitch = 0.0;
			return new double[]{0.0, 0.0};
		}
		if (!driftStarted && Math.max(Math.abs(dYaw), Math.abs(dPitch)) > 6.0) {
			driftStarted = true;
			if (random.nextDouble() < 0.25) {
				double mag = 0.15 * noiseAmp * (0.2 + random.nextDouble());
				if (Math.abs(dYaw) >= Math.abs(dPitch)) {
					burstYaw = Math.signum(dYaw) * mag;
				} else {
					burstPitch = Math.signum(dPitch) * mag;
				}
			}
		}
		double t = nowMs * 0.001;
		double ny = noiseYaw.sampleOctaves(t * octaveYaw, 3);
		double np = noisePitch.sampleOctaves(t * octavePitch, 3);
		double sine = Math.sin(t * (Math.PI * 2) * sineFreq + sinePhase);
		double outYaw = noiseAmp * (0.3 * ny + 0.1 * sine) + burstYaw;
		double outPitch = noiseAmp * (0.24 * np + 0.06 * sine) + burstPitch;
		double decay = Math.exp(-dt / 0.22);
		burstYaw *= decay;
		burstPitch *= decay;
		return new double[]{outYaw, outPitch};
	}

	private double motor(double cur, double goal, boolean isYaw, double dt) {
		double delta = isYaw ? MathHelper.wrapDegrees((float)(goal - cur)) : goal - cur;
		double absDelta = Math.abs(delta);
		double maxStep = maxSpeed * confidence * (isYaw ? 1.0 : 0.9);
		double k = Math.min(followWindow, 0.5 / dt);
		double req = Math.signum(delta) * Math.min(maxStep, k * absDelta);
		double ease = 1.0 - Math.exp(-k * dt);
		double vel = isYaw ? velYaw : velPitch;
		vel += (req - vel) * ease;
		double out = vel * dt;
		if (Math.abs(out) > absDelta) {
			out = delta;
			vel = delta / dt * 0.45;
		}
		if (absDelta <= Math.max(0.012, 0.0) && Math.abs(out) <= 0.0) {
			out = 0.0;
			vel *= 0.35;
		}
		if (isYaw) velYaw = vel; else velPitch = vel;
		return quantize(out, isYaw, absDelta);
	}

	private double quantize(double out, boolean isYaw, double absDelta) {
		if (smallSettle(isYaw, absDelta, out)) return 0.0;
		return out;
	}

	private boolean smallSettle(boolean isYaw, double absDelta, double out) {
		return absDelta <= 0.012 && Math.abs(out) <= 0.0;
	}

	private void reseed() {
		octaveYaw = 0.1 + random.nextDouble() * 0.15;
		octavePitch = 0.1 + random.nextDouble() * 0.15;
		sineFreq = 0.6 + random.nextDouble();
		sinePhase = random.nextDouble() * (Math.PI * 2);
		driftStarted = false;
	}

	private static final class FractalNoise {
		private final int seed = new Random().nextInt();

		double sampleOctaves(double x, int octaves) {
			return fractal(x, octaves, 2.0, 0.5);
		}

		private double lattice(int i) {
			int h = i * 374761393 + seed * 668265263;
			h = (h ^ (h >>> 13)) * 1274126177;
			h ^= h >>> 16;
			return (h & 0x7fffffff) / 2.147483647e9;
		}

		private double sample(double x) {
			int i = (int) Math.floor(x);
			double f = smoothstep(x - i);
			double a = lattice(i), b = lattice(i + 1);
			return (a + (b - a) * f) * 2.0 - 1.0;
		}

		private double fractal(double x, int octaves, double lac, double gain) {
			double amp = 1.0, freq = 1.0, sum = 0.0, norm = 0.0;
			int n = Math.max(1, octaves);
			for (int i = 0; i < n; i++) {
				sum += sample(x * freq) * amp;
				norm += amp;
				freq *= lac;
				amp *= gain;
			}
			return norm <= 0 ? 0 : sum / norm;
		}

		private static double smoothstep(double t) {
			t = t < 0 ? 0 : t > 1 ? 1 : t;
			return t * t * (3.0 - 2.0 * t);
		}
	}
}
