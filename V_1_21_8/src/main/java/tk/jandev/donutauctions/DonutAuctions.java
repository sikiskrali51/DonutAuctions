package tk.jandev.donutauctions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import tk.jandev.donutauctions.util.FormattingUtil;
import tk.jandev.donutauctions.util.WrappedStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static tk.jandev.donutauctions.ItemCache.CacheResult.MONEY_COLOR;

public class DonutAuctions extends DonutAuctionsCommon<Text> implements ClientModInitializer {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        String dotMinecraft = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
        String fullPath = dotMinecraft + "/config/donutauctions";

        super.setup(fullPath);
    }

    @Override
    protected void registerGameMessageListener(Consumer<String> listener) {
        ClientReceiveMessageEvents.GAME.register((text, b) -> listener.accept(text.getString()));
    }

    @Override
    protected void sendPlayerMessage(String message) {
        mc.player.sendMessage(Text.literal(message), false);
    }

    @Override
    public WrappedStack createWrappedStack(Object nativeStack) {
        ItemStack itemStack = (ItemStack) nativeStack;
        if (isShulkerBox(itemStack)) return wrapShulker(itemStack);

        String id = Registries.ITEM.getId(itemStack.getItem()).getPath();
        int count = itemStack.getCount();

        Map<String, Integer> enchants = new HashMap<>();

        ItemEnchantmentsComponent enchantmentComponent = itemStack.getEnchantments();

        for (var entry : enchantmentComponent.getEnchantments()) {
            int level = enchantmentComponent.getLevel(entry);

            String enchantmentName = entry.getIdAsString();

            enchants.put(enchantmentName, level);
        }

        return new WrappedStack(id, count, enchants, null);
    }

    @Override
    public boolean shouldRenderFor(Object nativeStack) {
        ItemStack stack = (ItemStack) nativeStack;

        if (mc.player == null) return false;
        if (!isClientOnDonutSMP()) return false;
        if (mc.player.getInventory().contains(stack)) return true;
        return mc.player.currentScreenHandler.getStacks().contains(stack);
    }

    private WrappedStack wrapShulker(ItemStack shulkerStack) {
        ContainerComponent containerComponent = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (containerComponent == null) {
            return null;
        }
        List<ItemStack> stacks = containerComponent.stream().toList();

        return new WrappedStack(
                "minecraft:shulker_box",
                1, Map.of(),
                stacks.stream().map(this::createWrappedStack).toList()
        );
    }

    private boolean isShulkerBox(ItemStack itemStack) {
        return itemStack.getComponents().contains(DataComponentTypes.CONTAINER);
    }

    @Override
    protected boolean isClientOnDonutSMP() {
        if (Math.random() < 5.0) return true;
        if (mc.getCurrentServerEntry() != null) {
            ServerInfo info = mc.getCurrentServerEntry();
            String address = info.address;

            Matcher matcher = DONUTSMP_DOMAIN_PATTERN.matcher(address);
            return (matcher.matches());
        }

        return false;
    }

    @Override
    public List<Text> getTextFor(ItemCache.CacheResult cacheResult, int stackCount, int maxStackCount) {
        switch (cacheResult.status()) {
            case LOADING -> {
                return List.of(Text.literal("§7Loading.."));
            }
            case SUCCESS -> {
                List<Text> result = new ArrayList<>();
                double pricePerItem = cacheResult.priceData();

                long actualPrice = (long) pricePerItem * stackCount;
                long pricePerFullStack = (long) pricePerItem * maxStackCount;
                long pricePerShulker = pricePerFullStack * 27;

                Text lineOne = Text.literal("§7Auction-Value: ")
                        .append(Text.literal("$" + FormattingUtil.formatCurrency(actualPrice)).styled(style -> style.withColor(MONEY_COLOR)));

                result.add(lineOne);

                if (shouldRenderSingularPrice()) {
                    Text pricePerItemLine = Text.literal("§7Price Per Item: ").append(FormattingUtil.formatCurrency((long) pricePerItem)).styled(style -> style.withColor(MONEY_COLOR));
                    result.add(pricePerItemLine);
                }

                if (shouldRenderShulkerPrice()) {
                    Text pricePerShulkerLine = Text.literal("§7Price Per Shulker: ").append(FormattingUtil.formatCurrency(pricePerShulker)).styled(style -> style.withColor(MONEY_COLOR));
                    result.add(pricePerShulkerLine);
                }

                return result;
            }
            case NO_API_KEY -> {
                return List.of(Text.literal("§cType /api to set your API-Key"));
            }
            case NO_RESULTS -> {
                return List.of(Text.literal("§7No Auctions Found"));
            }
        };
        return List.of();
    }
}
