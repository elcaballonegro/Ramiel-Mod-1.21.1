package dev.ramiel.entity;

import dev.ramiel.Form;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Which way Ramiel's model points. Used by BOTH the renderer and the hit test, so what you see
 * is what gets hit.
 *
 *  - spinning forms (dormant, drill, sunburst, urchin) turn around the vertical axis
 *  - facing forms (lance, cross, sentinel, star) point their local +Z at the aim direction
 *  - the shield form turns one triangular face of the octahedron toward the aim direction
 *
 * While morphing, the old and new orientation are blended.
 */
public final class Orientation {
    private Orientation() {}

    public static Quaternionf of(RamielEntity r, float partial) {
        float m = Mth.clamp(r.morph(partial), 0f, 1f);
        Quaternionf to = forForm(r, r.form(), partial);
        if (m >= 1f || r.formFrom() == r.form()) return to;
        Quaternionf from = forForm(r, r.formFrom(), partial);
        float eased = m * m * (3f - 2f * m);
        return from.slerp(to, eased);
    }

    public static Quaternionf forForm(RamielEntity r, Form form, float partial) {
        return forAngles(form, r.aimYaw(partial), r.aimPitch(partial), r.spin(partial));
    }

    /** The same rule from plain numbers (degrees), for things that have no entity, like the horizon version. */
    public static Quaternionf forAngles(Form form, float aimYawDeg, float aimPitchDeg, float spinDeg) {
        switch (form) {
            case SHIELD: {
                // a face normal of the octahedron is (1,1,1)/sqrt(3); turn it toward the threat
                Vector3f face = new Vector3f(1f, 1f, 1f).normalize();
                net.minecraft.world.phys.Vec3 aim = net.minecraft.world.phys.Vec3.directionFromRotation(aimPitchDeg, aimYawDeg);
                return new Quaternionf().rotationTo(face, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
            }
            case LANCE:
            case CROSS:
            case SENTINEL:
            case STAR:
                return new Quaternionf().rotationYXZ(-(float) Math.toRadians(aimYawDeg), (float) Math.toRadians(aimPitchDeg), 0f);
            default:
                return new Quaternionf().rotationY((float) Math.toRadians(spinDeg));
        }
    }
}
