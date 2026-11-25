// Anime-style impact frame for Rimulib's FxApi.
// Uniforms:  Strength 0..1 (how much of the effect to apply)
//            Mode     0 or 1 (alternates every tick so the frame flickers between two looks)
// Rimulib provides: texCoord, fragColor, DiffuseSampler (the scene).

uniform float Strength;
uniform float Mode;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float lum = dot(scene, vec3(0.299, 0.587, 0.114));

    // squash the picture into three flat tones, like a hand-inked frame
    float t = smoothstep(0.16, 0.20, lum) * 0.5 + smoothstep(0.52, 0.58, lum) * 0.5;
    vec3 ink = mix(vec3(0.02, 0.02, 0.07), vec3(1.0), t);

    // speed lines radiating from the centre
    vec2 p = texCoord - 0.5;
    float angle = atan(p.y, p.x);
    float rad = length(p);
    float line = step(0.78, fract(sin(floor(angle * 64.0) * 91.7) * 437.5));
    ink = mix(ink, vec3(1.0), line * smoothstep(0.12, 0.55, rad) * 0.85);

    // mode 0: the "positive" frame; mode 1: the same, inverted
    vec3 look = mix(ink, 1.0 - ink, Mode);
    // a hint of warm colour so it does not look like a plain filter
    look *= mix(vec3(1.0), vec3(1.0, 0.9, 0.8), 1.0 - Mode);

    fragColor = vec4(mix(scene, look, clamp(Strength, 0.0, 1.0)), 1.0);
}
