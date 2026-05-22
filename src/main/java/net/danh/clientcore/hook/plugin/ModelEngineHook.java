package net.danh.clientcore.hook.plugin;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.render.DisplayBone;
import com.ticxo.modelengine.api.model.render.DisplayFire;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import com.ticxo.modelengine.api.model.render.ModelRenderer;
import org.bukkit.entity.Entity;

import java.util.HashSet;
import java.util.Set;

public final class ModelEngineHook {
    private ModelEngineHook() {
    }

    public static Set<Integer> renderEntityIds(Entity entity) {
        try {
            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
            if (modeledEntity == null) return Set.of();

            Set<Integer> ids = new HashSet<>();
            for (ActiveModel model : modeledEntity.getModels().values()) {
                collectRenderEntityIds(model, ids);
            }
            return ids;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    public static Set<Integer> renderEntityIds(ActiveModel model) {
        try {
            Set<Integer> ids = new HashSet<>();
            collectRenderEntityIds(model, ids);
            return ids;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static void collectRenderEntityIds(ActiveModel model, Set<Integer> ids) {
        ModelRenderer renderer = model.getModelRenderer();
        if (!(renderer instanceof DisplayRenderer display)) return;

        ids.add(display.getPivot().getId());

        DisplayRenderer.Hitbox hitbox = display.getHitbox();
        ids.add(hitbox.getPivotId());
        ids.add(hitbox.getHitboxId());
        ids.add(hitbox.getShadowId());

        for (DisplayFire fire : hitbox.getFireDisplay().getAll()) {
            ids.add(fire.getId());
        }

        display.getSpawnQueue().values().forEach(bone -> collectBoneEntityIds(bone, ids));
        display.getRendered().values().forEach(bone -> collectBoneEntityIds(bone, ids));
    }

    private static void collectBoneEntityIds(DisplayBone bone, Set<Integer> ids) {
        for (DisplayBone.BoneData data : bone.getModel().values()) {
            ids.add(data.getId());
        }
    }
}
