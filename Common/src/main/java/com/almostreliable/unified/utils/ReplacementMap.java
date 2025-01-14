package com.almostreliable.unified.utils;

import com.almostreliable.unified.AlmostUnified;
import com.almostreliable.unified.api.StoneStrataHandler;
import com.almostreliable.unified.config.UnifyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class ReplacementMap {

    private final TagMap tagMap;
    private final UnifyConfig unifyConfig;
    private final StoneStrataHandler stoneStrataHandler;

    public ReplacementMap(TagMap tagMap, StoneStrataHandler stoneStrataHandler, UnifyConfig unifyConfig) {
        this.tagMap = tagMap;
        this.unifyConfig = unifyConfig;
        this.stoneStrataHandler = stoneStrataHandler;
    }

    @Nullable
    public UnifyTag<Item> getPreferredTagForItem(ResourceLocation item) {
        Collection<UnifyTag<Item>> tags = tagMap.getTags(item);

        if (tags.isEmpty()) {
            return null;
        }

        if (tags.size() > 1) {
            AlmostUnified.LOG.warn(
                    "Item '{}' has multiple preferred tags '{}' for recipe replacement. This needs to be manually fixed by the user.",
                    item,
                    tags.stream().map(UnifyTag::location).toList());
        }

        return tags.iterator().next();
    }

    @Nullable
    public ResourceLocation getReplacementForItem(ResourceLocation item) {
        UnifyTag<Item> t = getPreferredTagForItem(item);
        if (t == null) {
            return null;
        }

        if (stoneStrataHandler.isStoneStrataTag(t)) {
            String stone = stoneStrataHandler.getStoneStrata(item);
            return getPreferredItemForTag(t, i -> stone.equals(stoneStrataHandler.getStoneStrata(i)));
        }

        return getPreferredItemForTag(t, i -> true);
    }

    @Nullable
    public ResourceLocation getPreferredItemForTag(UnifyTag<Item> tag, Predicate<ResourceLocation> itemFilter) {
        List<ResourceLocation> items = tagMap
                .getItems(tag)
                .stream()
                .filter(itemFilter)
                // Helps us to get the clean stone variant first in case of a stone strata tag
                .sorted(Comparator.comparingInt(value -> value.toString().length()))
                .toList();

        ResourceLocation overrideItem = getOverrideForTag(tag, items);
        if (overrideItem != null) {
            return overrideItem;
        }

        for (String modPriority : unifyConfig.getModPriorities()) {
            ResourceLocation item = findItemByNamespace(items, modPriority);
            if (item != null) return item;
        }

        return null;
    }

    @Nullable
    private ResourceLocation getOverrideForTag(UnifyTag<Item> tag, List<ResourceLocation> items) {
        String priorityOverride = unifyConfig.getPriorityOverrides().get(tag.location());
        if (priorityOverride != null) {
            ResourceLocation item = findItemByNamespace(items, priorityOverride);
            if (item != null) return item;
            AlmostUnified.LOG.warn(
                    "Priority override mod '{}' for tag '{}' does not contain a valid item. Falling back to default priority.",
                    priorityOverride,
                    tag.location());
        }
        return null;
    }

    @Nullable
    private ResourceLocation findItemByNamespace(List<ResourceLocation> items, String namespace) {
        for (ResourceLocation item : items) {
            if (item.getNamespace().equals(namespace)) {
                return item;
            }
        }
        return null;
    }
}
