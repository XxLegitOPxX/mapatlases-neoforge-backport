package pepjebs.mapatlases.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import pepjebs.mapatlases.MapAtlasesMod;
import pepjebs.mapatlases.capabilities.MapCollectionCap;
import pepjebs.mapatlases.integration.SupplementariesCompat;
import pepjebs.mapatlases.item.MapAtlasItem;
import pepjebs.mapatlases.utils.MapDataHolder;

import java.lang.ref.WeakReference;

public class AntiqueAtlasRecipe extends CustomRecipe {

    private WeakReference<Level> levelRef = new WeakReference<>(null);

    public AntiqueAtlasRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (!MapAtlasesMod.SUPPLEMENTARIES) return false;
        ItemStack atlas = ItemStack.EMPTY;
        ItemStack ink = ItemStack.EMPTY;
        // ensure 1 and one only atlas
        for (int j = 0; j < inv.getContainerSize(); ++j) {
            ItemStack itemstack = inv.getItem(j);
            if (itemstack.is(MapAtlasesMod.MAP_ATLAS.get())) {
                if (!atlas.isEmpty()) return false;
                if (SupplementariesCompat.hasAntiqueInk(itemstack)) return false;
                atlas = itemstack;
            } else if (SupplementariesCompat.isAntiqueInk(itemstack)) {
                if (!ink.isEmpty()) return false;
                ink = itemstack;
            } else if (!itemstack.isEmpty()) return false;
        }
        if (!atlas.isEmpty() && !ink.isEmpty()) {
            levelRef = new WeakReference<>(level);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryManager) {

        Level level = levelRef.get();
        ItemStack newAtlas = ItemStack.EMPTY;
        ItemStack oldAtlas = ItemStack.EMPTY;
        // ensure 1 and one only atlas
        for (int j = 0; j < inv.getContainerSize(); ++j) {
            ItemStack itemstack = inv.getItem(j);
            if (itemstack.is(MapAtlasesMod.MAP_ATLAS.get())) {
                newAtlas = itemstack.copyWithCount(1);
                oldAtlas = itemstack;
            }
        }

        // Get the Map Ids in the Grid
        // Set NBT Data
        MapCollectionCap maps = MapAtlasItem.getMaps(newAtlas, level);
        MapCollectionCap oldMaps = MapAtlasItem.getMaps(oldAtlas, level);
        for (MapDataHolder holder : maps.getAll()) {
            oldMaps.remove(holder);
            Integer newId = SupplementariesCompat.createAntiqueMapData(holder.data,level,true, false);
            if(newId != null) oldMaps.add(newId, level);
        }
        SupplementariesCompat.setAntiqueInk(newAtlas);
        return newAtlas;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MapAtlasesMod.MAP_ANTIQUE_RECIPE.get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

}
