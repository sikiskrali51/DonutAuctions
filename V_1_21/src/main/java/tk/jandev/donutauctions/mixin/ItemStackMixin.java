package tk.jandev.donutauctions.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
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

    @Shadow public abstract int getMaxCount();

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE", // inject into the place where LORE is applied!
                    target = "Lnet/minecraft/item/ItemStack;appendTooltip(Lnet/minecraft/component/ComponentType;Lnet/minecraft/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/item/tooltip/TooltipType;)V",
                    shift = At.Shift.AFTER,
                    ordinal = 5
            )
    )
    public void appendAfterLore(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir, @Local List<Text> tooltip) {
        if (player == null) return;
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
