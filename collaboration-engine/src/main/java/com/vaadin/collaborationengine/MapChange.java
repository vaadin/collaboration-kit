/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Add-On License 3.0
 * (CVALv3).
 *
 * See the file licensing.txt distributed with this software for more
 * information about licensing.
 *
 * You should have received a copy of the license along with this program.
 * If not, see <http://vaadin.com/license/cval-3>.
 */
package com.vaadin.collaborationengine;

/**
 * A single change that is emitted from the map data of a {@link Topic}.
 *
 * @author Vaadin Ltd
 */
public class MapChange extends AbstractMapChange {
    private final Object oldValue;

    public MapChange(String mapName, String key, Object oldValue,
            Object newValue) {
        super(mapName, key, newValue);
        this.oldValue = oldValue;
    }

    public Object getOldValue() {
        return oldValue;
    }
}
