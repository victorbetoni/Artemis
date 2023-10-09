package com.wynntils.models.consumables;

import com.wynntils.core.components.Model;
import com.wynntils.models.consumables.type.ConsumableType;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;

import java.util.List;
import java.util.Map;

public class GenericConsumableModel extends Model {
    private String name;
    private Map<StatType, RangedValue> stats;
    private ConsumableType type;
    protected GenericConsumableModel(List<Model> dependencies) {
        super(dependencies);
    }


    public String getName() {
        return name;
    }

    public Map<StatType, RangedValue> getStats() {
        return stats;
    }

    public ConsumableType getType() {
        return type;
    }
}
