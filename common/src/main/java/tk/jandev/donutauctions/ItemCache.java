package tk.jandev.donutauctions;



import tk.jandev.donutauctions.util.WrappedStack;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemCache {
    private AuctionScraper scraper;
    private final RateLimiter rateLimiter = new RateLimiter(220, 60); // Slightly below donut-imposed rate limits in order to account for imprecision

    private final Map<DonutItem, CacheResult> priceCache = new ConcurrentHashMap<>();
    private final Set<DonutItem> currentlyRequesting = new ConcurrentSkipListSet<>(Comparator.comparing(DonutItem::id)); // comparison order is irrelevant for us, we just need thread safety!

    private final ExecutorService threadPool = Executors.newFixedThreadPool(25); // 25 threads to query items *should* be enough!

    private final long cacheExpiration;

    private final int maxPriceUpdates;
    private final int maxPagesWithNoUpdates;

    private final WrappedStack.Factory stackFactory;

    public ItemCache(long cacheExpiration, SearchConfig searchConfig, WrappedStack.Factory stackFactory) {
        this.cacheExpiration = cacheExpiration;

        this.maxPriceUpdates = searchConfig.maxPriceUpdates();
        this.maxPagesWithNoUpdates = searchConfig.maxPagesWithNoUpdate();
        this.stackFactory = stackFactory;
    }

    public CacheResult getPrice(Object nativeStack) {
        return getPrice(this.stackFactory.create(nativeStack));
    }

    public CacheResult getPrice(WrappedStack wrappedStack) {
        if (this.scraper == null) return CacheResult.NO_API_KEY; // make it clear to the client that they need to set their API-key
        if (wrappedStack.isShulker()) return handleShulkerBox(wrappedStack);
        DonutItem key = DonutItem.ofItemStack(wrappedStack);

        if (!priceCache.containsKey(key)) {
            queryAndCacheAsync(key);

            return new CacheResult(CacheResultStatus.LOADING, 0, System.currentTimeMillis());
        }

        CacheResult result = priceCache.get(key);
        if (result.shouldBeRenewed(this, System.currentTimeMillis())) queryAndCacheAsync(key);

        return result;
    }

    private void queryAndCacheAsync(DonutItem key) {
        if (currentlyRequesting.contains(key)) return;
        currentlyRequesting.add(key);

        threadPool.submit(() -> {
            try {
                rateLimiter.acquire(); // in case we have currently maxed out our requests, wait until we have not maxed our requests!

                Double foundPrice = this.scraper.findCheapestMatchingPrice(key.id, key.enchants, this.maxPriceUpdates, this.maxPagesWithNoUpdates);

                CacheResult result;
                if (foundPrice == null || foundPrice == Double.POSITIVE_INFINITY) {
                    result = new CacheResult(CacheResultStatus.NO_RESULTS, 0, System.currentTimeMillis());
                } else {
                    result = CacheResult.withData(foundPrice);
                }

                this.priceCache.put(key, result);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            currentlyRequesting.remove(key);
        });
    }

    private CacheResult handleShulkerBox(WrappedStack shulker) {
        List<WrappedStack> stacks = shulker.shulkerContents(); // method will only ever be called when shulkerContents is non-null

        long sum = 0;
        for (WrappedStack stack : stacks) {
            CacheResult subResult = getPrice(stack);

            if (subResult.hasData()) {
                sum += (long) (subResult.priceData * stack.count());
            }
        }

        return CacheResult.withData(sum);
    }

    public void supplyAPIKey(String key) {
        this.scraper = new AuctionScraper(key);
    }

    private record DonutItem(String id, Map<String, Integer> enchants) {
        public static DonutItem ofItemStack(WrappedStack stack) {
            return new DonutItem(stack.materialName(), stack.enchants());
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public record CacheResult(CacheResultStatus status, double priceData, long acquireTime) {
        public final static int MONEY_COLOR = new Color(1, 252, 0, 255).getRGB();

        public static CacheResult NO_API_KEY = new CacheResult(CacheResultStatus.NO_API_KEY, 0, 0);

        public static CacheResult withData(double priceData) {
            return new CacheResult(CacheResultStatus.SUCCESS, priceData, System.currentTimeMillis());
        }

        public boolean shouldBeRenewed(ItemCache cache, long currentTime) {
            return (currentTime - acquireTime > cache.cacheExpiration);
        }

        public boolean hasData() {
            return status == CacheResultStatus.SUCCESS;
        }
    }

    public enum CacheResultStatus {
        SUCCESS,
        NO_RESULTS,
        NO_API_KEY,
        LOADING
    }

    public record SearchConfig(int maxPriceUpdates, int maxPagesWithNoUpdate) {

    }
}
