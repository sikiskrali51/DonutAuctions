package tk.jandev.donutauctions.util;

public class ComparisonUtil {
    public static boolean matchesWithoutNamespaceOrMatches(String a, String b) {
        return removeNamespaceIfPossible(a).equalsIgnoreCase(removeNamespaceIfPossible(b));
    }

    private static String removeNamespaceIfPossible(String key) {
        String[] split = key.split(":");
        if (split.length == 1) return key;
        if (split.length != 2) throw new IllegalArgumentException("key: " + key + " contains illegal characters");

        return key.replace(split[0] + ":", "");
    }
}
