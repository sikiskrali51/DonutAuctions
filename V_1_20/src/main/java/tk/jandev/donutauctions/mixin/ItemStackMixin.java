package tk.jandev.donutauctions.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.ItemCache;

import java.util.List;

@Mixin(ItemStack.class)
@Environment(EnvType.CLIENT)
public abstract class ItemStackMixin {
    @Shadow public abstract int getCount();

    @Shadow public abstract boolean hasNbt();

    @Shadow public abstract int getMaxCount();

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtCompound;contains(Ljava/lang/String;I)Z",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void appendAfterLore(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir, @Local(ordinal = 0) List<Text> tooltip) {
        if (player == null) return;
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        if (donutAuctions.shouldRenderFor(this)) {
            appendTooltip(tooltip);
        }
    }

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;hasNbt()Z",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void appendAfterLoreNoNBT(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir, @Local(ordinal = 0) List<Text> tooltip) {
        if (player == null) return;
        if (this.hasNbt()) return; // we've already appended our component after lore was appended

        final DonutAuctions donutAuctions = DonutAuctions.getInstance();
        if (donutAuctions.shouldRenderFor(this)) {
            appendTooltip(tooltip);
        }
    }

    @Unique
    private void appendTooltip(List<Text> tooltip) {
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        ItemCache.CacheResult cacheResult = donutAuctions.getItemCache().getPrice(this);
        int count = this.getCount();

        List<Text> messages = donutAuctions.getTextFor(cacheResult, count, this.getMaxCount());

        tooltip.addAll(messages);
    }
}
