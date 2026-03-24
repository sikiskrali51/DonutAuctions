package tk.jandev.donutauctions.util;


import java.util.List;
import java.util.Map;

public record WrappedStack(String materialName, int count, Map<String, Integer> enchants, List<WrappedStack> shulkerContents) {
    public boolean isShulker() {
        return shulkerContents != null;
    }

    public interface Factory {
        WrappedStack create(Object nativeStack);
    }
}
