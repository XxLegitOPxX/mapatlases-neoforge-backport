package pepjebs.mapatlases.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {

    @Mutable
    @Accessor("centerX")
    void setCenterX(int center);

    @Mutable
    @Accessor("centerZ")
    void setCenterZ(int center);

    @Mutable
    @Accessor("dimension")
    void setDimension(ResourceKey<Level> dimension);

    @Invoker("addDecoration")
    void invokeAddDecoration(MapDecoration.Type pType, @Nullable LevelAccessor pLevel, String pDecorationName, double pLevelX,
                             double pLevelZ, double pRotation, @Nullable Component pName);

    @Accessor("bannerMarkers")
    Map<String, MapBanner> getBannerMarkers();

    @Invoker("removeDecoration")
    void invokeRemoveDecoration(String pIdentifier);


}
