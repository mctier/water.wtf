package com.utilitymod.echo.utils.rotation;

import java.util.Random;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Port of the mochi client's HumanizedAimController (continuous path):
 * target-point tracking with motion prediction, exp low-pass velocity steering,
 * per-axis wobble noise, flick impulses and a residual GCD quantizer.
 *
 * <p>Semantics are tick-scaled: call once per client tick with {@code dtTicks} =
 * ticks since the previous call (1.0 at standard 20Hz). Steps are returned
 * relative to the current look; the caller owns applying them.
 */
public final class HumanizedAimController {

    private static final double TWO_PI = Math.PI * 2;

    private final Random random;
    private final HumanizedNoise noiseYaw = new HumanizedNoise();
    private final HumanizedNoise noisePitch = new HumanizedNoise();

    // -- profile (mochi RotationProfile, continuous path fields) --
    private double maxSpeed = 360.0;
    private double randomness = 0.0;
    private double momentumCap = 5.0;
    private double predictLead = 0.0;
    private boolean pitchDisabled = false;

    // -- noise phases (refreshed when the randomness amplitude moves) --
    private double noiseFreqYaw;
    private double noiseFreqPitch;
    private double sineFrequency;
    private double sinePhase;

    // -- steering state --
    private double yawVelocity;
    private double pitchVelocity;
    private double yawSlew;
    private double pitchSlew;
    private double yawWobble;
    private double pitchWobble;
    private double yawFlick;
    private double pitchFlick;
    private boolean flickArmed;
    private double speedScale = 1.0;

    // -- target-point tracker --
    private Vec3d trackedPoint;
    private Vec3d lastTrackedPoint;
    private Vec3d trackedVelocity;
    private long lastTrackerTime = -1000L;
    private int velocitySamples;
    private boolean hasTrackedPoint;

    public HumanizedAimController() {
        this(new Random());
    }

    public HumanizedAimController(Random random) {
        this.random = random;
        this.refreshNoisePhases();
    }

    /**
     * mochi RotationProfile wiring for the continuous path: maxSpeed in the
     * deg-per-frame scale the rotation model consumes, wobbleUnit as the 0..1
     * amplitude of the wobble/flick field, momentumUnit feeding the
     * 5 + 25u velocity cap, predictLead as a 0..1 unit tracker weight.
     */
    public void configure(double maxSpeed, double wobbleUnit, double momentumUnit,
                          double predictLeadUnit, boolean pitchDisabled) {
        this.maxSpeed = Math.max(0.0, maxSpeed);
        this.randomness = Math.max(0.0, Math.min(1.0, wobbleUnit));
        double candidateCap = 5.0 + 25.0 * Math.max(0.0, Math.min(1.0, momentumUnit));
        if (Math.abs(candidateCap - this.momentumCap) > 1.0E-6) {
            this.momentumCap = candidateCap;
            this.refreshNoisePhases();
        }
        this.predictLead = Math.max(0.0, Math.min(1.0, predictLeadUnit));
        this.pitchDisabled = pitchDisabled;
    }

    private void refreshNoisePhases() {
        this.noiseFreqYaw = 0.1 + this.random.nextDouble() * 0.15;
        this.noiseFreqPitch = 0.1 + this.random.nextDouble() * 0.15;
        this.sineFrequency = 0.6 + this.random.nextDouble();
        this.sinePhase = this.random.nextDouble() * (Math.PI * 2);
    }

    /** Release steering state; {@code hard} also drops the point tracker. */
    public void reset(boolean hard) {
        this.yawVelocity = 0.0;
        this.pitchVelocity = 0.0;
        this.yawSlew = 0.0;
        this.pitchSlew = 0.0;
        this.yawWobble = 0.0;
        this.pitchWobble = 0.0;
        this.yawFlick = 0.0;
        this.pitchFlick = 0.0;
        this.flickArmed = false;
        this.speedScale = 1.0;
        if (hard) {
            this.hasTrackedPoint = false;
            this.velocitySamples = 0;
            this.trackedVelocity = null;
        }
    }

    /**
     * One humanized step.
     *
     * @param fromX/fromY/fromZ observer position angles aim from
     * @param targetPoint       aim point in world, or null for angle-only steering
     * @param nowMillis         absolute wall clock for the wobble timeline
     * @param maxStepDeg        per-call snap quantum (sensitivity GCD; &lt;= 0 keeps raw)
     */
    public float[] process(
            float curYaw, float curPitch,
            double fromX, double fromY, double fromZ,
            Vec3d targetPoint, long nowMillis,
            float maxStepDeg, double dtTicks
    ) {
        double dt = Math.max(dtTicks, 0.05) * 0.016666666666666666;

        double steerYaw = curYaw;
        double steerPitch = curPitch;
        if (targetPoint != null) {
            this.trackTargetPoint(targetPoint, nowMillis);
            float[] smoothed = aimAngles(
                    fromX, fromY, fromZ,
                    this.trackedPoint.x, this.trackedPoint.y, this.trackedPoint.z,
                    curYaw);
            steerYaw = curYaw + MathHelper.wrapDegrees(smoothed[0] - curYaw);
            steerPitch = smoothed[1];
            if (this.predictLead > 0.0 && this.velocitySamples >= 3) {
                double speed = Math.sqrt(
                        this.trackedVelocity.x * this.trackedVelocity.x +
                        this.trackedVelocity.y * this.trackedVelocity.y +
                        this.trackedVelocity.z * this.trackedVelocity.z);
                if (speed >= 0.35) {
                    double lead = 0.08 * this.predictLead;
                    double leadX = this.trackedVelocity.x * lead;
                    double leadY = this.trackedVelocity.y * lead * 0.5;
                    double leadZ = this.trackedVelocity.z * lead;
                    double leadLength = Math.sqrt(leadX * leadX + leadY * leadY + leadZ * leadZ);
                    if (leadLength > 1.0) {
                        double normalize = 1.0 / leadLength;
                        leadX *= normalize;
                        leadY *= normalize;
                        leadZ *= normalize;
                    }
                    float[] predicted = aimAngles(
                            fromX, fromY, fromZ,
                            this.trackedPoint.x + leadX, this.trackedPoint.y + leadY, this.trackedPoint.z + leadZ,
                            curYaw);
                    steerYaw = curYaw + MathHelper.wrapDegrees(predicted[0] - curYaw);
                    steerPitch = predicted[1];
                }
            }
        }

        double yawDiff = MathHelper.wrapDegrees((float) steerYaw - curYaw);
        double pitchDiff = steerPitch - curPitch;

        this.walkSpeedScale(dt);
        this.updateWobble(nowMillis, dt, yawDiff, pitchDiff);

        double yawStep = this.stepAxis(curYaw, curYaw + yawDiff + this.yawWobble, true, dt, maxStepDeg);
        double pitchStep = this.pitchDisabled
                ? 0.0
                : this.stepAxis(curPitch, curPitch + pitchDiff + this.pitchWobble, false, dt, maxStepDeg);
        return new float[]{(float) yawStep, (float) pitchStep};
    }

    private void trackTargetPoint(Vec3d point, long nowMillis) {
        double seconds = (double) (nowMillis - this.lastTrackerTime) / 1000.0;
        double move = this.lastTrackedPoint == null ? 0.0 : point.distanceTo(this.lastTrackedPoint);
        if (this.hasTrackedPoint && !(seconds > 0.5) && !(move > 4.0)) {
            if (seconds > 1.0E-4) {
                double lerp = 1.0 - Math.exp(-seconds / 0.04);
                this.trackedPoint = this.trackedPoint == null ? point : new Vec3d(
                        this.trackedPoint.x + (point.x - this.trackedPoint.x) * lerp,
                        this.trackedPoint.y + (point.y - this.trackedPoint.y) * lerp,
                        this.trackedPoint.z + (point.z - this.trackedPoint.z) * lerp);
                double instantX = (point.x - this.lastTrackedPoint.x) / seconds;
                double instantY = (point.y - this.lastTrackedPoint.y) / seconds;
                double instantZ = (point.z - this.lastTrackedPoint.z) / seconds;
                double ema = this.velocitySamples <= 1 ? 1.0 : 0.28;
                this.trackedVelocity = this.trackedVelocity == null ? new Vec3d(instantX, instantY, instantZ) : new Vec3d(
                        this.trackedVelocity.x + (instantX - this.trackedVelocity.x) * ema,
                        this.trackedVelocity.y + (instantY - this.trackedVelocity.y) * ema,
                        this.trackedVelocity.z + (instantZ - this.trackedVelocity.z) * ema);
                this.velocitySamples++;
            }
            this.lastTrackedPoint = point;
            this.lastTrackerTime = nowMillis;
        } else {
            this.trackedPoint = point;
            this.lastTrackedPoint = point;
            this.trackedVelocity = new Vec3d(0.0, 0.0, 0.0);
            this.velocitySamples = 1;
            this.lastTrackerTime = nowMillis;
            this.hasTrackedPoint = true;
        }
    }

    private void walkSpeedScale(double dt) {
        double amp = 0.012 + 0.02 * this.randomness;
        this.speedScale = MathHelper.clamp(
                this.speedScale + this.random.nextGaussian() * amp * Math.sqrt(dt), 0.9, 1.1);
    }

    private void updateWobble(long nowMillis, double dt, double yawDiff, double pitchDiff) {
        if (this.randomness <= 0.0) {
            this.yawWobble = 0.0;
            this.pitchWobble = 0.0;
            this.yawFlick = 0.0;
            this.pitchFlick = 0.0;
            return;
        }
        if (!this.flickArmed && Math.max(Math.abs(yawDiff), Math.abs(pitchDiff)) > 6.0) {
            this.flickArmed = true;
            if (this.random.nextDouble() < 0.25) {
                double amp = 0.15 * this.randomness * (0.2 + this.random.nextDouble());
                if (Math.abs(yawDiff) >= Math.abs(pitchDiff)) {
                    this.yawFlick = Math.signum(yawDiff) * amp;
                } else {
                    this.pitchFlick = Math.signum(pitchDiff) * amp;
                }
            }
        }
        double seconds = (double) nowMillis * 0.001;
        double yawNoise = this.noiseYaw.sampleOctaves(seconds * this.noiseFreqYaw, 3);
        double pitchNoise = this.noisePitch.sampleOctaves(seconds * this.noiseFreqPitch, 3);
        double sine = Math.sin(seconds * TWO_PI * this.sineFrequency + this.sinePhase);
        this.yawWobble = this.randomness * (0.3 * yawNoise + 0.1 * sine) + this.yawFlick;
        this.pitchWobble = this.randomness * (0.24 * pitchNoise + 0.06 * sine) + this.pitchFlick;
        double decay = Math.exp(-dt / 0.22);
        this.yawFlick *= decay;
        this.pitchFlick *= decay;
    }

    /** mochi computeDouble: exp low-pass velocity drive with overshoot/arrival guards. */
    private double stepAxis(double cur, double aimed, boolean yawAxis, double dt, float gcd) {
        double delta = yawAxis
                ? MathHelper.wrapDegrees((float) (aimed - cur))
                : aimed - cur;
        double absDelta = Math.abs(delta);
        double velocity = yawAxis ? this.yawVelocity : this.pitchVelocity;
        double momentLimit = Math.min(this.momentumCap, 0.5 / dt);
        double speedTarget = this.maxSpeed * this.speedScale * (yawAxis ? 1.0 : 0.9);
        double velocityTarget = Math.signum(delta) * Math.min(speedTarget, momentLimit * absDelta);
        double lowpass = 1.0 - Math.exp(-momentLimit * dt);
        velocity += (velocityTarget - velocity) * lowpass;
        double turn = velocity * dt;
        if (Math.abs(turn) > absDelta) {
            turn = delta;
            velocity = delta / dt * 0.45;
        }
        if (absDelta <= Math.max(0.012, (double) gcd * 0.5) && Math.abs(turn) <= (double) gcd) {
            velocity *= 0.35;
            turn = 0.0;
        }
        turn = this.quantizeStep(turn, yawAxis, gcd);
        if (yawAxis) {
            this.yawVelocity = velocity;
        } else {
            this.pitchVelocity = velocity;
        }
        return turn;
    }

    /** mochi computeDouble7: residual GCD accumulator with occasional +-1 step hops. */
    private double quantizeStep(double turn, boolean yawAxis, float gcd) {
        if (gcd <= 0.0F) {
            return turn;
        }
        double slew = yawAxis ? (this.yawSlew += turn) : (this.pitchSlew += turn);
        if (Math.abs(slew) < 1.0E-5) {
            return 0.0;
        }
        long steps = Math.round(slew / (double) gcd);
        if (steps != 0L) {
            double hopChance = (yawAxis ? 0.05 : 0.03) + (yawAxis ? 0.45 : 0.3) * this.randomness;
            if (this.random.nextDouble() < hopChance) {
                steps += this.random.nextBoolean() ? 1L : -1L;
            }
        }
        double quantized = (double) steps * (double) gcd;
        if (yawAxis) {
            this.yawSlew = slew - quantized;
        } else {
            this.pitchSlew = slew - quantized;
        }
        return quantized;
    }

    /** mochi RotationMath.computeFloatArray: absolute angles from {@code from} to {@code to}, yaw anchored. */
    public static float[] aimAngles(
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ,
            float anchorYaw
    ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yawAngle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitchAngle = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
        float yawContinuous = anchorYaw + MathHelper.wrapDegrees(yawAngle - anchorYaw);
        return new float[]{yawContinuous, MathHelper.clamp(pitchAngle, -90.0f, 90.0f)};
    }
}
