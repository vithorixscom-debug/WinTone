/*
 * This file is part of Winton, a modified version of Baritone.
 * Copyright (c) Baritone contributors.
 * Copyright (c) WinTone contributors, for modifications.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 */

package winton.behavior;

import winton.Winton;
import winton.api.Settings;
import winton.api.WintonAPI;
import winton.api.behavior.ILookBehavior;
import winton.api.behavior.look.IAimProcessor;
import winton.api.behavior.look.ITickableAimProcessor;
import winton.api.event.events.*;
import winton.api.event.events.type.EventState;
import winton.api.utils.IPlayerContext;
import winton.api.utils.Rotation;
import winton.behavior.look.ForkableRandom;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.Optional;

public final class LookBehavior extends Behavior implements ILookBehavior {

    /**
     * The current look target, may be {@code null}.
     */
    private Target target;

    /**
     * The rotation known to the server. Returned by {@link #getEffectiveRotation()} for use in {@link IPlayerContext}.
     */
    private Rotation serverRotation;

    /**
     * The last player rotation. Used to restore the player's angle when using free look.
     *
     * @see Settings#freeLook
     */
    private Rotation prevRotation;

    private final AimProcessor processor;

    public LookBehavior(Winton winton) {
        super(winton);
        this.processor = new AimProcessor(winton.getPlayerContext());
    }

    @Override
    public void updateTarget(Rotation rotation, boolean blockInteract) {
        if (!blockInteract && Winton.settings().lookInWalkDirection.value) {
            float pitch = rotation.getPitch();
            // Eyes follow the path, but a person looks a little down the ground ahead.
            float gaze;
            if (pitch < -35) {
                gaze = -18 + (pitch + 35) * 0.35F;
            } else if (pitch > 28) {
                gaze = 14 + (pitch - 28) * 0.35F;
            } else {
                gaze = 8F + pitch * 0.42F;
            }
            rotation = new Rotation(rotation.getYaw(), gaze);
        }
        this.target = new Target(rotation, Target.Mode.resolve(ctx, blockInteract), blockInteract);
    }

    /** True while aiming at a dig/place target this tick. */
    public boolean isBlockAiming() {
        return this.target != null && this.target.blockInteract;
    }

    /**
     * Exact dig/place angle for this tick. Raytraces must use this, not the lagged
     * server rotation, or break/place never lands (freeLook + smooth look).
     */
    public Rotation interactionAim() {
        if (this.target != null && this.target.blockInteract) {
            return this.target.rotation;
        }
        return null;
    }

    @Override
    public IAimProcessor getAimProcessor() {
        return this.processor;
    }

    @Override
    public void onTick(TickEvent event) {
        // Idle: don't burn RNG / aim jitter when nothing is aiming
        if (event.getType() == TickEvent.Type.IN) {
            if (this.target != null) {
                this.processor.tick();
            } else {
                this.processor.idle();
            }
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {

        if (this.target == null) {
            if (event.getState() == EventState.PRE) {
                // Keep last camera blend alive — snapping visual off every idle tick
                // is what made walk-look feel like a slideshow.
                this.holdVisual();
            }
            return;
        }

        switch (event.getState()) {
            case PRE: {
                if (this.target.mode == Target.Mode.NONE) {
                    // Just return for PRE, we still want to set target to null on POST
                    return;
                }

                this.prevRotation = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                final Rotation actual = this.processor.commitLook(this.target.rotation);
                if (this.target.mode == Target.Mode.CLIENT) {
                    this.presentVisual(this.prevRotation.getYaw(), this.prevRotation.getPitch(), actual.getYaw(), actual.getPitch());
                } else {
                    ctx.player().setYRot(actual.getYaw());
                    ctx.player().setXRot(actual.getPitch());
                }
                break;
            }
            case POST: {
                // Silent look restores the camera. Client look keeps the spring pose.
                if (this.prevRotation != null && this.target.mode == Target.Mode.SERVER) {
                    ctx.player().setYRot(this.prevRotation.getYaw());
                    ctx.player().setXRot(this.prevRotation.getPitch());
                }
                this.prevRotation = null;
                // The target is done being used for this game tick, so it can be invalidated
                this.target = null;
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onSendPacket(PacketEvent event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) {
            return;
        }

        final ServerboundMovePlayerPacket packet = (ServerboundMovePlayerPacket) event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket.Rot || packet instanceof ServerboundMovePlayerPacket.PosRot) {
            this.serverRotation = new Rotation(packet.getYRot(0.0f), packet.getXRot(0.0f));
        }
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        stopAim();
        this.serverRotation = null;
    }

    /** Full look teardown — call on kill/stop so camera/FPS recover. */
    public void stopAim() {
        this.target = null;
        this.prevRotation = null;
        this.processor.resetEye();
        this.releaseVisual();
    }

    /**
     * Frame camera. Smoothstep between the last two settled angles, with yaw wrapped,
     * so the spring reads as one continuous turn even at low FPS.
     */
    public boolean sampleCamera(float partial, float[] yawPitch) {
        if (!this.visual) {
            return false;
        }
        float t = partial < 0F ? 0F : (partial > 1F ? 1F : partial);
        // light ease — full smootherstep felt laggy
        t = t * t * (3F - 2F * t);
        yawPitch[0] = this.visFromYaw + wrapDegrees(this.visToYaw - this.visFromYaw) * t;
        yawPitch[1] = this.visFromPitch + (this.visToPitch - this.visFromPitch) * t;
        return true;
    }

    private float visFromYaw;
    private float visToYaw;
    private float visFromPitch;
    private float visToPitch;
    private boolean visual;

    private void presentVisual(float fromYaw, float fromPitch, float toYaw, float toPitch) {
        var player = ctx.player();
        boolean liteSnap = false;
        try {
            liteSnap = winton.getBuilderProcess().isLitematicaBuilding() && this.target != null && this.target.blockInteract;
        } catch (Throwable ignored) {
        }
        this.visFromYaw = fromYaw;
        this.visFromPitch = fromPitch;
        this.visToYaw = toYaw;
        this.visToPitch = toPitch;
        this.visual = true;
        // Litematica: less frame blend so the cursor feels faster
        if (liteSnap) {
            player.yRotO = fromYaw + wrapDegrees(toYaw - fromYaw) * 0.55F;
            player.xRotO = fromPitch + (toPitch - fromPitch) * 0.55F;
        } else {
            player.yRotO = fromYaw;
            player.xRotO = fromPitch;
        }
        player.setYRot(toYaw);
        player.setXRot(toPitch);
        player.yHeadRotO = liteSnap ? player.yRotO : fromYaw;
        player.yHeadRot = toYaw;
        float body = player.yBodyRot;
        player.yBodyRotO = body;
        player.yBodyRot = body + wrapDegrees(toYaw - body) * (liteSnap ? 0.55F : 0.28F);
    }

    private void holdVisual() {
        var player = ctx.player();
        if (player == null) {
            return;
        }
        if (!this.visual) {
            return;
        }
        // Collapse the blend onto the settled eye so the next partial tick
        // doesn't rewind, but keep sampleCamera active.
        float y = player.getYRot();
        float p = player.getXRot();
        this.visFromYaw = y;
        this.visToYaw = y;
        this.visFromPitch = p;
        this.visToPitch = p;
        player.yRotO = y;
        player.xRotO = p;
        player.yHeadRotO = player.yHeadRot;
        player.yBodyRotO = player.yBodyRot;
    }

    private void releaseVisual() {
        if (!this.visual) {
            return;
        }
        this.visual = false;
        var player = ctx.player();
        if (player == null) {
            return;
        }
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        player.yHeadRotO = player.yHeadRot;
        player.yBodyRotO = player.yBodyRot;
    }

    private static float wrapDegrees(float delta) {
        delta %= 360F;
        if (delta >= 180F) {
            delta -= 360F;
        }
        if (delta < -180F) {
            delta += 360F;
        }
        return delta;
    }

    public void pig() {
        if (this.target != null) {
            final Rotation actual = this.processor.peekRotation(this.target.rotation);
            ctx.player().setYRot(actual.getYaw());
        }
    }

    public Optional<Rotation> getEffectiveRotation() {
        if (Winton.settings().freeLook.value) {
            return Optional.ofNullable(this.serverRotation);
        }
        // If freeLook isn't on, just defer to the player's actual rotations
        return Optional.empty();
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        if (this.target != null) {
            // Physics uses the path heading now. The camera spring stays in sampleCamera.
            event.setYaw(this.target.rotation.getYaw());
            event.setPitch(this.processor.peekRotation(this.target.rotation).getPitch());
        }
    }

    private static final class AimProcessor extends AbstractAimProcessor {

        public AimProcessor(final IPlayerContext ctx) {
            super(ctx);
        }

        @Override
        protected Rotation getPrevRotation() {
            // Implementation will use LookBehavior.serverRotation
            return ctx.playerRotations();
        }
    }

    private static abstract class AbstractAimProcessor implements ITickableAimProcessor {

        protected final IPlayerContext ctx;
        private final ForkableRandom rand;
        private double randomYawOffset;
        private double randomPitchOffset;
        private float eyeYaw;
        private float eyePitch;
        private float velYaw;
        private float velPitch;
        private boolean eyeLive;
        private int committedTick = Integer.MIN_VALUE;
        private float committedWantYaw;
        private float committedWantPitch;
        private Rotation committed;

        public AbstractAimProcessor(IPlayerContext ctx) {
            this.ctx = ctx;
            this.rand = new ForkableRandom();
        }

        private AbstractAimProcessor(final AbstractAimProcessor source) {
            this.ctx = source.ctx;
            this.rand = source.rand.fork();
            this.randomYawOffset = source.randomYawOffset;
            this.randomPitchOffset = source.randomPitchOffset;
            this.eyeYaw = source.eyeYaw;
            this.eyePitch = source.eyePitch;
            this.velYaw = source.velYaw;
            this.velPitch = source.velPitch;
            this.eyeLive = source.eyeLive;
        }

        @Override
        public final Rotation peekRotation(final Rotation rotation) {
            int tick = this.ctx.player() == null ? -1 : this.ctx.player().tickCount;
            if (this.committed != null
                    && tick == this.committedTick
                    && rotation.getYaw() == this.committedWantYaw
                    && rotation.getPitch() == this.committedWantPitch) {
                return this.committed;
            }
            return this.integrate(rotation, false);
        }

        /** One human look-step. Peek previews it; the player update commits it. */
        public final Rotation commitLook(final Rotation rotation) {
            return this.integrate(rotation, true);
        }

        public final void idle() {
            this.velYaw *= 0.45F;
            this.velPitch *= 0.45F;
        }

        public final void resetEye() {
            this.eyeLive = false;
            this.velYaw = 0F;
            this.velPitch = 0F;
            this.committed = null;
            this.committedTick = Integer.MIN_VALUE;
        }

        @Override
        public final void tick() {
            boolean digging = this.isDigging();
            double lookAmp = digging ? 0.0D : 0.35D;
            double sampleYaw = (this.rand.nextDouble() - 0.5) * Winton.settings().randomLooking.value * lookAmp;
            double samplePitch = (this.rand.nextDouble() - 0.5) * Winton.settings().randomLooking.value * lookAmp * 0.55D;
            if (!digging) {
                double random = this.rand.nextDouble() - 0.5;
                if (Math.abs(random) < 0.1) {
                    random *= 4;
                }
                sampleYaw += random * Winton.settings().randomLooking113.value * 0.35D;
            }
            // Low-pass so the aim doesn't buzz a new offset every tick.
            this.randomYawOffset += (sampleYaw - this.randomYawOffset) * 0.12D;
            this.randomPitchOffset += (samplePitch - this.randomPitchOffset) * 0.12D;
        }

        @Override
        public final void advance(int ticks) {
            for (int i = 0; i < ticks; i++) {
                this.tick();
            }
        }

        @Override
        public Rotation nextRotation(final Rotation rotation) {
            final Rotation actual = this.commitLook(rotation);
            this.tick();
            return actual;
        }

        @Override
        public final ITickableAimProcessor fork() {
            return new AbstractAimProcessor(this) {

                private Rotation prev = AbstractAimProcessor.this.getPrevRotation();

                @Override
                public Rotation nextRotation(final Rotation rotation) {
                    return (this.prev = super.nextRotation(rotation));
                }

                @Override
                protected Rotation getPrevRotation() {
                    return this.prev;
                }
            };
        }

        protected abstract Rotation getPrevRotation();

        /**
         * Nudges the player's pitch to a regular level. (Between {@code -20} and {@code 10}, increments are by {@code 1})
         */
        private float nudgeToLevel(float pitch) {
            if (pitch < -20) {
                return pitch + 1;
            } else if (pitch > 10) {
                return pitch - 1;
            }
            return pitch;
        }

        private boolean isDigging() {
            try {
                var w = WintonAPI.getProvider().getWintonForMinecraft(ctx.minecraft());
                if (w instanceof Winton ww) {
                    return ww.getLookBehavior().isBlockAiming();
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        private boolean isLitematicaBuildAim() {
            try {
                var w = WintonAPI.getProvider().getWintonForMinecraft(ctx.minecraft());
                if (w instanceof Winton ww) {
                    return ww.getLookBehavior().isBlockAiming()
                            && ww.getBuilderProcess().isLitematicaBuilding();
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        /**
         * Critically damped head. No overshoot, no mouse-pixel steps.
         * Several substeps per tick keep the curve smooth instead of a straight snap.
         */
        private Rotation integrate(Rotation want, boolean commit) {
            Rotation prev = this.getPrevRotation();
            if (!this.eyeLive) {
                this.eyeYaw = prev.getYaw();
                this.eyePitch = prev.getPitch();
                this.velYaw = 0F;
                this.velPitch = 0F;
                this.eyeLive = true;
            } else if (Math.abs(wrap(prev.getYaw() - this.eyeYaw)) > 50F
                    || Math.abs(prev.getPitch() - this.eyePitch) > 50F) {
                this.eyeYaw = prev.getYaw();
                this.eyePitch = prev.getPitch();
                this.velYaw = 0F;
                this.velPitch = 0F;
            }

            boolean digging = this.isDigging();
            boolean liteBuild = this.isLitematicaBuildAim();
            boolean elytra = this.ctx.player() != null && this.ctx.player().isFallFlying();
            Settings settings = Winton.settings();
            // Litematica place/break: fast aim, little spring — other movement keeps the smooth look
            boolean smooth = !liteBuild && (elytra ? settings.elytraSmoothLook.value : settings.smoothLook.value);
            float desiredYaw = want.getYaw();
            float desiredPitch = want.getPitch();
            if (!smooth) {
                desiredYaw += (float) this.randomYawOffset;
                desiredPitch += (float) this.randomPitchOffset;
            }
            if (!liteBuild && want.getPitch() == prev.getPitch()) {
                desiredPitch = nudgeToLevel(desiredPitch);
            }

            // Litematica: almost snap so crosshair lands on the place face quickly
            if (liteBuild) {
                float errY = wrap(desiredYaw - this.eyeYaw);
                float errP = desiredPitch - this.eyePitch;
                float snap = 0.72F; // most of the way each tick — fast, not buttery
                float eyeY = this.eyeYaw + errY * snap;
                float eyeP = Rotation.clampPitch(this.eyePitch + errP * snap);
                if (Math.abs(errY) < 2.5F && Math.abs(errP) < 2.5F) {
                    eyeY = desiredYaw;
                    eyeP = Rotation.clampPitch(desiredPitch);
                }
                Rotation shown = new Rotation(eyeY, eyeP);
                if (commit) {
                    this.velYaw = 0F;
                    this.velPitch = 0F;
                    this.eyeYaw = eyeY;
                    this.eyePitch = eyeP;
                    this.committedTick = this.ctx.player() == null ? this.committedTick + 1 : this.ctx.player().tickCount;
                    this.committedWantYaw = want.getYaw();
                    this.committedWantPitch = want.getPitch();
                    this.committed = shown;
                }
                return shown;
            }

            // Responsive path track — still sprung, not a snap.
            int ticks = Math.max(4, Math.min(16, settings.smoothLookTicks.value));
            float omega = smooth ? (78F / ticks) : 28F;
            float yawErr = Math.abs(wrap(desiredYaw - this.eyeYaw));
            if (yawErr > 35F) {
                omega *= 2.0F;
            } else if (yawErr > 12F) {
                omega *= 1.4F;
            }
            if (digging) {
                omega *= 1.55F;
            }
            if (elytra) {
                omega *= 1.25F;
            }
            float eyeY = this.eyeYaw;
            float eyeP = this.eyePitch;
            float velY = this.velYaw;
            float velP = this.velPitch;
            final int steps = 12;
            float h = 0.05F / steps;
            for (int i = 0; i < steps; i++) {
                float errY = wrap(desiredYaw - eyeY);
                float errP = desiredPitch - eyeP;
                velY += (omega * omega * errY - 2F * omega * velY) * h;
                velP += (omega * omega * errP - 2F * omega * velP) * h;
                float maxVel = smooth ? 95F : 140F;
                if (velY > maxVel) velY = maxVel;
                if (velY < -maxVel) velY = -maxVel;
                if (velP > maxVel * 0.6F) velP = maxVel * 0.6F;
                if (velP < -maxVel * 0.6F) velP = -maxVel * 0.6F;
                eyeY += velY * h;
                eyeP = Rotation.clampPitch(eyeP + velP * h);
            }
            Rotation shown = new Rotation(eyeY, eyeP);
            if (commit) {
                this.velYaw = velY;
                this.velPitch = velP;
                this.eyeYaw = eyeY;
                this.eyePitch = eyeP;
                this.committedTick = this.ctx.player() == null ? this.committedTick + 1 : this.ctx.player().tickCount;
                this.committedWantYaw = want.getYaw();
                this.committedWantPitch = want.getPitch();
                this.committed = shown;
            }
            return shown;
        }

        private static float wrap(float delta) {
            delta %= 360F;
            if (delta >= 180F) {
                delta -= 360F;
            }
            if (delta < -180F) {
                delta += 360F;
            }
            return delta;
        }

        private static float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        private float quantizeDelta(float delta) {
            return mouseToAngle(angleToMouse(delta));
        }

        private double angleToMouse(float angleDelta) {
            final float minAngleChange = mouseToAngle(1);
            return Math.round(angleDelta / minAngleChange);
        }

        private float mouseToAngle(double mouseDelta) {
            // casting float literals to double gets us the precise values used by mc
            final double f = ctx.minecraft().options.sensitivity().get() * (double) 0.6f + (double) 0.2f;
            return (float) (mouseDelta * f * f * f * 8.0d) * 0.15f; // yes, one double and one float scaling factor
        }
    }

    private static class Target {

        public final Rotation rotation;
        public final Mode mode;
        public final boolean blockInteract;

        public Target(Rotation rotation, Mode mode, boolean blockInteract) {
            this.rotation = rotation;
            this.mode = mode;
            this.blockInteract = blockInteract;
        }

        enum Mode {
            /**
             * Rotation will be set client-side and is visual to the player
             */
            CLIENT,

            /**
             * Rotation will be set server-side and is silent to the player
             */
            SERVER,

            /**
             * Rotation will remain unaffected on both the client and server
             */
            NONE;

            static Mode resolve(IPlayerContext ctx, boolean blockInteract) {
                final Settings settings = Winton.settings();
                final boolean antiCheat = settings.antiCheatCompatibility.value;
                final boolean blockFreeLook = settings.blockFreeLook.value;

                if (ctx.player().isFallFlying()) {
                    // always need to set angles while flying
                    return settings.elytraFreeLook.value ? SERVER : CLIENT;
                } else if (settings.freeLook.value) {
                    // Regardless of if antiCheatCompatibility is enabled, if a blockInteract is requested then the player
                    // rotation needs to be set somehow, otherwise Winton will halt since objectMouseOver() will just be
                    // whatever the player is mousing over visually. Let's just settle for setting it silently.
                    if (blockInteract) {
                        return blockFreeLook ? SERVER : CLIENT;
                    }
                    // Look where we walk must be CLIENT or the smooth spring never
                    // reaches the camera (SERVER restores mouse angles every POST).
                    if (settings.lookInWalkDirection.value) {
                        return CLIENT;
                    }
                    return antiCheat ? SERVER : NONE;
                }

                // all freeLook settings are disabled so set the angles
                return CLIENT;
            }
        }
    }
}
