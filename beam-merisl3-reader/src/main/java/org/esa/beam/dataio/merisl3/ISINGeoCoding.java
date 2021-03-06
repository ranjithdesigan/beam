/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.dataio.merisl3;

import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.AbstractGeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Scene;
import org.esa.beam.framework.dataop.maptransf.Datum;

/**
 * Experimental ISIN geo-coding for the MERIS binned Level-2 product.
 * This is not public API.
 */
public class ISINGeoCoding extends AbstractGeoCoding {

    private ISINGrid _grid;

    public ISINGeoCoding(ISINGrid grid) {
        _grid = grid;
    }

    @Override
    public boolean transferGeoCoding(Scene srcScene, Scene destScene, ProductSubsetDef subsetDef) {
        return false;
    }

    public boolean isCrossingMeridianAt180() {
        return false;
    }

    public boolean canGetPixelPos() {
        return true;
    }

    public boolean canGetGeoPos() {
        return true;
    }

    public PixelPos getPixelPos(final GeoPos geoPos, PixelPos pixelPos) {
        pixelPos = (pixelPos == null) ? new PixelPos() : pixelPos;
        pixelPos.x = -1;
        pixelPos.y = -1;
        int rowIndex = (int) ((90.0f - geoPos.lat) * _grid.getRowCount() / 180.0f);
        if (rowIndex >= 0 && rowIndex < _grid.getRowCount()) {
            int colIndex = (int) ((180.0f + geoPos.lon) / _grid.getDeltaLon(rowIndex));
            int rowLength = _grid.getRowLength(rowIndex);
            if (colIndex >= 0 && colIndex < rowLength) {
                pixelPos.x = _grid.getRowCount() - (rowLength / 2) + colIndex;
                pixelPos.y = rowIndex;
            }
        }
        return pixelPos;
    }

    public GeoPos getGeoPos(final PixelPos pixelPos, GeoPos geoPos) {
        geoPos = (geoPos == null) ? new GeoPos() : geoPos;
        geoPos.lat = -1;
        geoPos.lon = -1;
        int rowIndex = (int) pixelPos.y;
        if (rowIndex >= 0 && rowIndex < _grid.getRowCount()) {
            int rowLength = _grid.getRowLength(rowIndex);
            float colIndex = pixelPos.x - _grid.getRowCount() + (rowLength / 2);
            if (colIndex >= 0 && colIndex < rowLength) {
                geoPos.lat = 90.0f - 180.0f * (pixelPos.y) / _grid.getRowCount();
                geoPos.lon = (float) (colIndex * _grid.getDeltaLon(rowIndex) - 180f);
            }
        }
        return geoPos;
    }

    public Datum getDatum() {
        return Datum.WGS_84;
    }

    public void dispose() {
        _grid = null;
    }
}
