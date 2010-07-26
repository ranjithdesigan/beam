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

package org.esa.beam.dataio.netcdf.util;

import org.esa.beam.framework.datamodel.ProductData;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides some NetCDF related utility methods.
 */
public class ReaderUtils {

    public static ProductData createProductData(int productDataType, Array values) {
        Object data = values.getStorage();
        if (data instanceof char[]) {
            data = new String((char[]) data);
        }
        return ProductData.createInstance(productDataType, data);
    }

    public static boolean hasValidExtension(String pathname) {
        final String lowerPath = pathname.toLowerCase();
        final String[] validExtensions = Constants.FILE_EXTENSIONS;
        for (String validExtension : validExtensions) {
            validExtension = validExtension.toLowerCase();
            if (lowerPath.endsWith(validExtension)) {
                return true;
            }
        }
        return false;
    }

    public static Variable[] getVariables(List<Variable> variables, String[] names) {
        if (variables == null || names == null) {
            return null;
        }
        if (variables.size() < names.length) {
            return null;
        }
        final Variable[] result = new Variable[names.length];
        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            for (Variable variable : variables) {
                if (variable.getName().equalsIgnoreCase(name)) {
                    result[i] = variable;
                    break;
                }
            }
            if (result[i] == null) {
                return null;
            }
        }
        return result;
    }

    public static Map<String, Variable> createVariablesMap(List<Variable> globalVariables) {
        final HashMap<String, Variable> map = new HashMap<String, Variable>();
        for (Variable variable : globalVariables) {
            map.put(variable.getName(), variable);
        }
        return map;
    }

    public static boolean allElementsAreNotNull(final Object[] array) {
        if (array != null) {
            for (Object o : array) {
                if (o == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean allAttributesAreNotNullAndHaveTheSameSize(final Attribute[] attributes) {
        if (allElementsAreNotNull(attributes)) {
            final Attribute prim = attributes[0];
            for (int i = 1; i < attributes.length; i++) {
                if (prim.getLength() != attributes[i].getLength()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
