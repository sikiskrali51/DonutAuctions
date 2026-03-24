package tk.jandev.donutauctions.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.TooltipDisplayComponent;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import tk.jandev.donutauctions.DonutAuctions;
import tk.jandev.donutauctions.ItemCache;

import java.util.List;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
@Environment(EnvType.CLIENT)
public abstract class ItemStackMixin {
    @Shadow public abstract int getCount();

    @Shadow public abstract int getMaxCount();

    @Inject(
            method = "Lnet/minecraft/item/ItemStack;appendTooltip(Lnet/minecraft/item/Item$TooltipContext;Lnet/minecraft/component/type/TooltipDisplayComponent;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/tooltip/TooltipType;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE", // inject into the place where LORE is applied!
                    target = "Lnet/minecraft/item/ItemStack;appendComponentTooltip(Lnet/minecraft/component/ComponentType;Lnet/minecraft/item/Item$TooltipContext;Lnet/minecraft/component/type/TooltipDisplayComponent;Ljava/util/function/Consumer;Lnet/minecraft/item/tooltip/TooltipType;)V",
                    shift = At.Shift.AFTER,
                    ordinal = 18
            )
    )
    public void appendAfterLore(Item.TooltipContext context, TooltipDisplayComponent displayComponent, PlayerEntity player, TooltipType type, Consumer<Text> textConsumer, CallbackInfo ci) {
        if (player == null) return;
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        if (donutAuctions.shouldRenderFor(this)) {
            appendTooltip(textConsumer);
        }
    }


    @Unique
    private void appendTooltip(Consumer<Text> consumer) {
        final DonutAuctions donutAuctions = DonutAuctions.getInstance();

        ItemCache.CacheResult cacheResult = donutAuctions.getItemCache().getPrice(this);
        int count = this.getCount();

        List<Text> messages = donutAuctions.getTextFor(cacheResult, count, this.getMaxCount());

        messages.forEach(consumer);
    }
}
