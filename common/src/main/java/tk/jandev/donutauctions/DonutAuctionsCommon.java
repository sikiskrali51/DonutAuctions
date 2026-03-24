package tk.jandev.donutauctions;

import tk.jandev.donutauctions.config.SingleValueFile;
import tk.jandev.donutauctions.util.WrappedStack;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class DonutAuctionsCommon<TextClass> {
    private static DonutAuctionsCommon<?> instance;

    protected final Pattern API_TOKEN_PATTERN = Pattern.compile("Your API Token is: (\\w{32})");
    protected final Pattern DONUTSMP_DOMAIN_PATTERN = Pattern.compile("^([a-z0-9-]+\\.)*donutsmp\\.net$", Pattern.CASE_INSENSITIVE);

    private SingleValueFile apiKeyConfig;

    private ItemCache itemCache;

    public DonutAuctionsCommon() {
        instance = this;
    }

    protected void setup(String configPath) {
        this.apiKeyConfig = new SingleValueFile(configPath);

        this.itemCache = new ItemCache(
                300_000,
                new ItemCache.SearchConfig(6, 1000), // high number but whatever
                this::createWrappedStack
        );

        tryReadAPI();
        registerAPIMessageListener();
    }

    protected void registerAPIMessageListener() {
        registerGameMessageListener(message -> {
            if (!isClientOnDonutSMP()) return; // don't let other servers modify (break!) the API-key for users.
            Matcher matcher = API_TOKEN_PATTERN.matcher(message);

            if (matcher.find()) {
                String apiKey = matcher.group(1);

                sendPlayerMessage("§2Successfully Obtained API-Key for DonutAuctions!");

                this.itemCache.supplyAPIKey(apiKey);

                try {
                    apiKeyConfig.setAndWrite(apiKey);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    protected void tryReadAPI() {
        try {
            if (this.apiKeyConfig.read()) {
                String potentialAPIKey = this.apiKeyConfig.get();
                if (potentialAPIKey.length() != 32) {
                    this.apiKeyConfig.writeEmpty();
                    return;
                }

                this.itemCache.supplyAPIKey(potentialAPIKey);
            }
        } catch (IOException e) {

        }
    }

    public boolean shouldRenderShulkerPrice() {
        return false;
    }

    public boolean shouldRenderSingularPrice() {
        return false;
    }

    public ItemCache getItemCache() {
        return this.itemCache;
    }

    protected abstract void registerGameMessageListener(Consumer<String> listener);
    protected abstract void sendPlayerMessage(String message);
    public abstract WrappedStack createWrappedStack(Object nativeStack);
    public abstract boolean shouldRenderFor(Object nativeStack);

    protected abstract boolean isClientOnDonutSMP();
    public abstract List<TextClass> getTextFor(ItemCache.CacheResult cacheResult, int stackCount, int maxStackCount);


    public static <T extends DonutAuctionsCommon<?>> T getInstance() {
        return (T) instance;
    }
}
