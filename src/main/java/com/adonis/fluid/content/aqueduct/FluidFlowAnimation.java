package com.adonis.fluid.content.aqueduct;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FluidFlowAnimation {
    private float progress = 0;
    private float speed = 0.02f;
    private boolean flowing = true;
    
    public void tick() {
        if (flowing) {
            progress = (progress + speed) % 1.0f;
        }
    }
    
    public float getProgress() {
        return progress;
    }
    
    public void setProgress(float progress) {
        this.progress = Mth.clamp(progress, 0, 1);
    }
    
    public void setFlowing(boolean flowing) {
        this.flowing = flowing;
    }
    
    public void setSpeed(float speed) {
        this.speed = Mth.clamp(speed, 0, 0.1f);
    }
    
    public float getInterpolatedProgress(float partialTicks) {
        if (!flowing) return progress;
        return (progress + speed * partialTicks) % 1.0f;
    }
}