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

package org.esa.beam.dataio.netcdf.metadata;

import org.esa.beam.dataio.netcdf.util.RasterDigest;
import org.esa.beam.dataio.netcdf.util.VariableMap;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;
import java.util.Map;

/**
 * A context for reading metadata from netCDF into the BEAM product model.
 */
public interface ProfileReadContext {

    public void setProperty(String name, Object property);

    public Object getProperty(String name);

    public NetcdfFile getNetcdfFile();

    public VariableMap getRasterVariableMap();

    public List<Variable> getGlobalVariables();

    public RasterDigest getRasterDigest();

    public Map<String, Variable> getGlobalVariablesMap();
}