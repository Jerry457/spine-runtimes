/******************************************************************************
 * Spine Runtimes License Agreement
 * Last updated July 28, 2023. Replaces all prior versions.
 *
 * Copyright (c) 2013-2023, Esoteric Software LLC
 *
 * Integration of the Spine Runtimes into software or otherwise creating
 * derivative works of the Spine Runtimes is permitted under the terms and
 * conditions of Section 2 of the Spine Editor License Agreement:
 * http://esotericsoftware.com/spine-editor-license
 *
 * Otherwise, it is permitted to integrate the Spine Runtimes into software or
 * otherwise create derivative works of the Spine Runtimes (collectively,
 * "Products"), provided that each user of the Products must obtain their own
 * Spine Editor license and redistribution of the Products in any form must
 * include this license and copyright notice.
 *
 * THE SPINE RUNTIMES ARE PROVIDED BY ESOTERIC SOFTWARE LLC "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ESOTERIC SOFTWARE LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES,
 * BUSINESS INTERRUPTION, OR LOSS OF USE, DATA, OR PROFITS) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THE
 * SPINE RUNTIMES, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *****************************************************************************/

package com.esotericsoftware.spine;

import static com.esotericsoftware.spine.utils.SpineUtils.*;

import com.esotericsoftware.spine.Skeleton.Physics;

/** Stores the current pose for a physics constraint. A physics constraint applies physics to bones.
 * <p>
 * See <a href="http://esotericsoftware.com/spine-physics-constraints">Physics constraints</a> in the Spine User Guide. */
public class PhysicsConstraint implements Updatable {
	final PhysicsConstraintData data;
	public Bone bone;
	float inertia, strength, damping, mass, wind, gravity, mix;

	boolean reset = true;
	float ux, uy, cx, cy, tx, ty;
	float xOffset, xVelocity;
	float yOffset, yVelocity;
	float rotateOffset, rotateVelocity;
	float scaleOffset, scaleVelocity;

	boolean active;

	final Skeleton skeleton;
	float remaining, lastTime;

	public PhysicsConstraint (PhysicsConstraintData data, Skeleton skeleton) {
		if (data == null) throw new IllegalArgumentException("data cannot be null.");
		if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");
		this.data = data;
		this.skeleton = skeleton;
		bone = skeleton.bones.get(data.bone.index);
		inertia = data.inertia;
		strength = data.strength;
		damping = data.damping;
		mass = data.mass;
		wind = data.wind;
		gravity = data.gravity;
		mix = data.mix;
	}

	/** Copy constructor. */
	public PhysicsConstraint (PhysicsConstraint constraint) {
		if (constraint == null) throw new IllegalArgumentException("constraint cannot be null.");
		data = constraint.data;
		skeleton = constraint.skeleton;
		bone = constraint.bone;
		inertia = constraint.inertia;
		strength = constraint.strength;
		damping = constraint.damping;
		mass = constraint.mass;
		wind = constraint.wind;
		gravity = constraint.gravity;
		mix = constraint.mix;
	}

	public void reset () {
		remaining = 0;
		lastTime = skeleton.time;
		reset = true;
		xOffset = 0;
		xVelocity = 0;
		yOffset = 0;
		yVelocity = 0;
		rotateOffset = 0;
		rotateVelocity = 0;
		scaleOffset = 0;
		scaleVelocity = 0;
	}

	public void setToSetupPose () {
		reset();
		PhysicsConstraintData data = this.data;
		inertia = data.inertia;
		strength = data.strength;
		damping = data.damping;
		mass = data.mass;
		wind = data.wind;
		gravity = data.gravity;
		mix = data.mix;
	}

	/** Applies the constraint to the constrained bones. */
	public void update (Physics physics) {
		float mix = this.mix;
		if (mix == 0) return;

		boolean x = data.x, y = data.y, rotateOrShearX = data.rotate || data.shearX, scaleX = data.scaleX;
		Bone bone = this.bone;
		float l = bone.data.length;

		switch (physics) {
		case none:
			return;
		case reset:
			reset();
			// Fall through.
		case update:
			remaining += Math.max(skeleton.time - lastTime, 0);
			lastTime = skeleton.time;

			float bx = bone.worldX, by = bone.worldY;
			if (reset) {
				reset = false;
				ux = bx;
				uy = by;
			} else {
				float remaining = this.remaining, i = this.inertia, step = data.step;
				if (x || y) {
					if (x) {
						xOffset += (ux - bx) * i;
						ux = bx;
					}
					if (y) {
						yOffset += (uy - by) * i;
						uy = by;
					}
					if (remaining >= step) {
						float m = this.mass * step, e = this.strength, w = wind * 100, g = gravity * -100;
						float d = (float)Math.pow(this.damping, 60 * step);
						do {
							if (x) {
								xVelocity += (w - xOffset * e) * m;
								xOffset += xVelocity * step;
								xVelocity *= d;
							}
							if (y) {
								yVelocity += (g - yOffset * e) * m;
								yOffset += yVelocity * step;
								yVelocity *= d;
							}
							remaining -= step;
						} while (remaining >= step);
					}
					if (x) bone.worldX += xOffset * mix;
					if (y) bone.worldY += yOffset * mix;
				}
				if (rotateOrShearX || scaleX) {
					float ca = atan2(bone.c, bone.a), c, s;
					if (rotateOrShearX) {
						float dx = cx - bone.worldX, dy = cy - bone.worldY, r = atan2(dy + ty, dx + tx) - ca - rotateOffset * mix;
						rotateOffset += (r - (float)Math.ceil(r * invPI2 - 0.5f) * PI2) * i;
						r = rotateOffset * mix + ca;
						c = cos(r);
						s = sin(r);
						if (scaleX) scaleOffset += (dx * c + dy * s) * i / (l * bone.getWorldScaleX());
					} else {
						c = cos(ca);
						s = sin(ca);
						scaleOffset += ((cx - bone.worldX) * c + (cy - bone.worldY) * s) * i / (l * bone.getWorldScaleX());
					}
					remaining = this.remaining;
					if (remaining >= step) {
						float m = this.mass * step, e = this.strength, w = wind, g = gravity;
						float d = (float)Math.pow(this.damping, 60 * step);
						while (true) {
							remaining -= step;
							if (scaleX) {
								scaleVelocity += (w * c - g * s - scaleOffset * e) * m;
								scaleOffset += scaleVelocity * step;
								scaleVelocity *= d;
							}
							if (rotateOrShearX) {
								rotateVelocity += (-0.01f * l * (w * s + g * c) - rotateOffset * e) * m;
								rotateOffset += rotateVelocity * step;
								rotateVelocity *= d;
								if (remaining < step) break;
								float r = rotateOffset * mix + ca;
								c = cos(r);
								s = sin(r);
							} else if (remaining < step) //
								break;
						}
					}
				}
				this.remaining = remaining;
			}
			cx = bone.worldX;
			cy = bone.worldY;
			break;
		case pose:
			if (x) bone.worldX += xOffset * mix;
			if (y) bone.worldY += yOffset * mix;
		}

		if (rotateOrShearX) {
			float r = rotateOffset * mix, a = bone.a, s, c;
			if (data.rotate) {
				if (data.shearX) {
					r *= 0.5f;
					s = sin(r);
					c = cos(r);
					bone.a = c * a - s * bone.c;
					bone.c = s * a + c * bone.c;
					a = bone.a;
				} else {
					s = sin(r);
					c = cos(r);
				}
				float b = bone.b;
				bone.b = c * b - s * bone.d;
				bone.d = s * b + c * bone.d;
			} else {
				s = sin(r);
				c = cos(r);
			}
			bone.a = c * a - s * bone.c;
			bone.c = s * a + c * bone.c;
		}
		if (scaleX) {
			float s = 1 + scaleOffset * mix;
			bone.a *= s;
			bone.c *= s;
		}
		if (physics == Physics.update) {
			tx = l * bone.a;
			ty = l * bone.c;
		}
		bone.updateAppliedTransform();
	}

	/** The bone constrained by this physics constraint. */
	public Bone getBone () {
		return bone;
	}

	public void setBone (Bone bone) {
		this.bone = bone;
	}

	public float getInertia () {
		return inertia;
	}

	public void setInertia (float inertia) {
		this.inertia = inertia;
	}

	public float getStrength () {
		return strength;
	}

	public void setStrength (float strength) {
		this.strength = strength;
	}

	public float getDamping () {
		return damping;
	}

	public void setDamping (float damping) {
		this.damping = damping;
	}

	/** The inverse of the mass. */
	public float getMass () {
		return mass;
	}

	public void setMass (float mass) {
		this.mass = mass;
	}

	public float getWind () {
		return wind;
	}

	public void setWind (float wind) {
		this.wind = wind;
	}

	public float getGravity () {
		return gravity;
	}

	public void setGravity (float gravity) {
		this.gravity = gravity;
	}

	/** A percentage (0-1) that controls the mix between the constrained and unconstrained poses. */
	public float getMix () {
		return mix;
	}

	public void setMix (float mix) {
		this.mix = mix;
	}

	public boolean isActive () {
		return active;
	}

	/** The physics constraint's setup pose data. */
	public PhysicsConstraintData getData () {
		return data;
	}

	public String toString () {
		return data.name;
	}
}
