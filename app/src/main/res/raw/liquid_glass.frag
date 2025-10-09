// SimpleGlass.frag
uniform float3 iResolution;  // viewport (w,h,_)
uniform float uBlur;         // 0..10
uniform float uDistort;      // 0..0.05

float2 toUV(vec2 frag) { return frag / iResolution.xy; }
vec4 sampleBG(vec2 uv);

half4 main(vec2 fragCoord) {
    vec2 uv = toUV(fragCoord);

    // Add small random distortion
    vec2 distort = uv + (vec2(sin(uv.y*30.0), cos(uv.x*30.0)) - 0.5) * uDistort;
    distort = clamp(distort, 0.0, 1.0);

    // Simple 3x3 box blur
    vec2 step = vec2(1.0/iResolution.x, 1.0/iResolution.y) * uBlur;
    half4 color = half4(0.0);
    int count = 0;
    for(int x=-1;x<=1;x++){
        for(int y=-1;y<=1;y++){
            vec2 offset = vec2(x,y) * step;
            color += sampleBG(clamp(distort + offset,0.0,1.0));
            count++;
        }
    }
    color /= half(count);

    // Optional: add slight highlight/shine
    float shine = smoothstep(0.0, 1.0, uv.y) * 0.1;
    color.rgb += half3(shine);

    return color;
}

// Stub function for background sampling
vec4 sampleBG(vec2 uv){
    // iImage1 + iImageResolution could be used if needed
    // for now fallback to simple gradient
    return vec4(0.9,0.9,1.0,1.0);
}
