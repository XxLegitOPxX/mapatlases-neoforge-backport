package pepjebs.mapatlases.capabilities;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import pepjebs.mapatlases.utils.MapDataHolder;
import pepjebs.mapatlases.utils.MapType;
import pepjebs.mapatlases.utils.Slice;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

// For fabric.
// Or use cardinal components.
// Less optimized as it deserializes the stuff every time but at least doesn't have syncing issues
public class NBTMapCollection implements IMapCollection {
        ItemStack atlas;

    @Override
    public boolean add(int mapId, Level level) {
        return false;
    }

    @Override
    public boolean remove(MapDataHolder obj) {
        return false;
    }


    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public byte getScale() {
        return 0;
    }

    @Override
    public int[] getAllIds() {
        return new int[0];
    }

    @Override
    public Collection<ResourceKey<Level>> getAvailableDimensions() {
        return null;
    }

    @Override
    public Collection<MapType> getAvailableTypes(ResourceKey<Level> dimension) {
        return null;
    }

    @Override
    public TreeSet<Integer> getHeightTree(ResourceKey<Level> dimension, MapType type) {
        return null;
    }

    @Override
    public List<MapDataHolder> selectSection(Slice slice) {
        return null;
    }

    @Override
    public List<MapDataHolder> filterSection(Slice slice, Predicate<MapItemSavedData> predicate) {
        return null;
    }

    @Override
    public MapDataHolder select(MapKey key) {
        return null;
    }

    @Override
    public @Nullable MapDataHolder getClosest(double x, double z, Slice slice) {
        return null;
    }

    @Override
    public Collection<MapDataHolder> getAll() {
        return null;
    }
}
