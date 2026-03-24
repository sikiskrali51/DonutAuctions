package tk.jandev.donutauctions;

import com.google.gson.*;
import tk.jandev.donutauctions.util.ComparisonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class AuctionScraper {
    private static final String API_URL = "https://api.donutsmp.net/v1/auction/list/";

    private final String apiKey;
    private final HttpClient client;

    public AuctionScraper(String apiKey) {
        this.apiKey = apiKey;

        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Double findCheapestMatchingPrice(String searchQuery, Map<String, Integer> targetEnchantments, int maxPriceUpdates, int maxPageUpdatesWithNoUpdate) throws IOException, InterruptedException {
        int currentPage = 1;

        double bestFoundPriceSoFar = Double.POSITIVE_INFINITY;

        int bestPriceUpdateCount = 0;
        int pageUpdatesSinceLastBestPriceUpdate = 0;

        while (true) {
            JsonObject auctionPageResult = fetchAuctionPage(currentPage, searchQuery);
            JsonArray apiResponse = auctionPageResult.getAsJsonArray("result");
            if (apiResponse == null) {
                break;
            } // I have no idea how results.isEmpty() doesnt throw an error, but just freezes the thread - this check is important
            if (apiResponse.isEmpty()) {
                break;
            }

            boolean foundBetterItemOnThisPage = false;
            for (JsonElement responseElement : apiResponse) {
                double pricePerItem = getPriceForAuctionEntry(responseElement, targetEnchantments, searchQuery);
                if (pricePerItem == -1) continue;

                if (pricePerItem < bestFoundPriceSoFar) { // we do NOT want to use the first result that we find - it is (probably!) not the best there is
                    bestFoundPriceSoFar = pricePerItem;
                    bestPriceUpdateCount++;

                    foundBetterItemOnThisPage = true;
                    if (bestPriceUpdateCount >= maxPriceUpdates) {
                        return bestFoundPriceSoFar;
                    } // for some items, there are many, many pages of items. Often times, we will not find a "cheaper" item on the last 100 pages or so. For this reason, we just stop searching once we have beat our price a few times (maybe 3-4)
                }
            }
            if (!foundBetterItemOnThisPage) pageUpdatesSinceLastBestPriceUpdate++;

            if (pageUpdatesSinceLastBestPriceUpdate > maxPageUpdatesWithNoUpdate) {
                return bestFoundPriceSoFar;
            }
            currentPage++;
        }
        if (bestFoundPriceSoFar == Double.POSITIVE_INFINITY) return null; // TODO make query result more expressive

        return bestFoundPriceSoFar;
    }

    private double getPriceForAuctionEntry(JsonElement responseElement, Map<String, Integer> targetEnchantments, String targetMaterial) {
        if (responseElement.isJsonNull()) {
            return -1;
        }
        JsonObject auctionElement = responseElement.getAsJsonObject();
        JsonObject itemElement = auctionElement.getAsJsonObject("item");

        if (!matchesMaterial(itemElement, targetMaterial)) return -1;
        if (!matchesEnchantments(itemElement.getAsJsonObject("enchants"), targetEnchantments)) {
            return -1;
        }

        long price = auctionElement.get("price").getAsLong();
        int count = itemElement.get("count").getAsInt();

        return (double) price / count;
    }

    private JsonObject fetchAuctionPage(int page, String searchQuery) throws IOException, InterruptedException {
        URI url = URI.create(API_URL + page);

        ItemFilter filter = new ItemFilter(searchQuery, SortMode.LOWEST_PRICE);

        final HttpRequest request = HttpRequest
                .newBuilder(url)
                .method("GET", HttpRequest.BodyPublishers.ofString(filter.toString()))
                .header("Authorization", "Bearer " + this.apiKey)
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String content = response.body();

        JsonElement jsonElement = JsonParser.parseString(content);
        return jsonElement.getAsJsonObject();
    }

    private boolean matchesMaterial(JsonObject itemObject, String expectedMaterial) {
        if (!itemObject.has("id")) {
            System.out.println("itemobject didnt have id: " + itemObject);
            return false;
        }
        String auctionMat = itemObject.get("id").getAsString();

        return ComparisonUtil.matchesWithoutNamespaceOrMatches(auctionMat, expectedMaterial);
    }

    private boolean matchesEnchantments(JsonObject enchants, Map<String, Integer> targetEnchantments) {
        if (enchants == null || !enchants.has("enchantments")) return false;

        JsonObject enchantments = enchants.getAsJsonObject("enchantments");
        if (enchantments == null) {
            return targetEnchantments.isEmpty();
        }
        if (!enchantments.has("levels") || enchantments.get("levels") instanceof JsonNull) {
            return targetEnchantments.isEmpty();
        }
        JsonObject levels = enchantments.getAsJsonObject("levels");

        for (Map.Entry<String, Integer> entry : targetEnchantments.entrySet()) {
            if (!levels.has(entry.getKey())) {
                return false;
            }

            for (var apiEntry : levels.keySet()) {
                if (ComparisonUtil.matchesWithoutNamespaceOrMatches(apiEntry, entry.getKey())) {
                    int level = levels.get(apiEntry).getAsInt();
                    if (entry.getValue() != level) return false;
                }
            }


        }

        // Check for enchantments we DONT want
        Set<String> itemEnchantKeys = levels.keySet();
        return itemEnchantKeys.size() == targetEnchantments.size();
    }

    public record ItemFilter(String itemName, SortMode sortMode) {
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append('"').append("search").append('"').append(":");
            builder.append('"');
            builder.append(itemName()).append('"').append(",");
            builder.append('"').append("sort").append('"').append(":");
            builder.append('"').append(sortMode()).append('"');
            builder.append("}");

            return builder.toString();
        }
    }

    public enum SortMode {
        LOWEST_PRICE,
        HIGHEST_PRICE,
        RECENTLY_LISTED,
        LAST_LISTED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
