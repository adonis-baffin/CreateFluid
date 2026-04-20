package com.adonis.fluid.mixin;

import com.adonis.fluid.goggle.PackagerGoggleInfo;
import com.adonis.fluid.util.IPackagerData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin implements IPackagerData, IHaveGoggleInformation {
    @Unique
    private String fluid$clipboardAddress = "";
    @Unique
    private static final Direction[] DIRECTIONS = Direction.values();

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void onWrite(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        compound.putString("FluidClipboardAddress", this.fluid$clipboardAddress);
    }

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private void onRead(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        this.fluid$clipboardAddress = compound.getString("FluidClipboardAddress");
    }

    @Override
    public String getClipboardAddress() {
        return this.fluid$clipboardAddress;
    }

    @Override
    public void setClipboardAddress(String address) {
        this.fluid$clipboardAddress = address;
    }

    @Unique
    private String fluid$readSignAddress(Direction side) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        Level level = packager.getLevel();
        if (level == null) {
            return "";
        }
        if (!(level.getBlockEntity(packager.getBlockPos().relative(side)) instanceof SignBlockEntity sign)) {
            return "";
        }
        for (boolean front : new boolean[]{true, false}) {
            SignText text = sign.getText(front);
            String address = "";

            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank()) {
                    address = address + string.trim() + " ";
                }
            }

            if (!address.isBlank()) {
                return address.trim();
            }
        }
        return "";
    }

    @Unique
    private void fluid$updateSignAddressFromSigns() {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        String newAddress = "";

        for (Direction side : DIRECTIONS) {
            String address = this.fluid$readSignAddress(side);
            if (!address.isBlank()) {
                newAddress = address;
                break;
            }
        }

        packager.signBasedAddress = newAddress;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        String address = "";
        if (packager.getLevel() != null && packager.getLevel().isClientSide) {
            this.fluid$updateSignAddressFromSigns();
        }

        if (!packager.signBasedAddress.isBlank()) {
            address = packager.signBasedAddress;
        } else {
            address = this.getClipboardAddress();
        }

        boolean isRepackager = packager instanceof RepackagerBlockEntity;
        PackagerGoggleInfo.addToTooltip(tooltip, address, isRepackager);
        return true;
    }
}
