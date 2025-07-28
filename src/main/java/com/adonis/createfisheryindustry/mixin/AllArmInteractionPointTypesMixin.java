

package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshInteractionPointType;
import com.adonis.createfisheryindustry.block.SmartNozzle.SmartNozzleInteractionPointType;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleInteractionPointType;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AllArmInteractionPointTypes.class)
public class AllArmInteractionPointTypesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void injectMeshTrapInteractionPointType(CallbackInfo ci) {
        try {
            Method registerMethod = AllArmInteractionPointTypes.class.getDeclaredMethod("register", String.class, ArmInteractionPointType.class);
            registerMethod.setAccessible(true);
            registerMethod.invoke(null, "mesh_trap", new MeshTrapInteractionPointType());
            registerMethod.invoke(null, "trap_nozzle", new TrapNozzleInteractionPointType());
            registerMethod.invoke(null, "smart_nozzle", new SmartNozzleInteractionPointType());
            registerMethod.invoke(null, "smart_mesh", new SmartMeshInteractionPointType());
        } catch (NoSuchMethodException e) {} catch (Exception e) {}
    }
}
