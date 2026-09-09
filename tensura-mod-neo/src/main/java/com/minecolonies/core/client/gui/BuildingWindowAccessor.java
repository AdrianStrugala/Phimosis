package com.minecolonies.core.client.gui;

import com.minecolonies.api.colony.buildings.views.IBuildingView;

public final class BuildingWindowAccessor {

    private BuildingWindowAccessor() {}

    public static IBuildingView getBuildingView(AbstractBuildingWindow<?> window) {
        return window.buildingView;
    }
}