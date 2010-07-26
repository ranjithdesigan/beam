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
package org.esa.beam.dataio.netcdf.metadata.profiles.beam;

import org.esa.beam.dataio.netcdf.metadata.ProfilePart;
import org.esa.beam.dataio.netcdf.metadata.ProfileReadContext;
import org.esa.beam.dataio.netcdf.metadata.ProfileWriteContext;
import org.esa.beam.dataio.netcdf.metadata.profiles.cf.CfIndexCodingPart;
import org.esa.beam.framework.dataio.ProductIOException;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.IndexCoding;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.Product;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

import java.io.IOException;

public class BeamIndexCodingPart extends ProfilePart {

    public static final String INDEX_DESCRIPTIONS = "index_descriptions";
    public static final String DESCRIPTION_SEPARATOR = "\t";
    public static final String INDEX_CODING_NAME = "index_coding_name";

    @Override
    public void read(ProfileReadContext ctx, Product p) throws IOException {
        final Band[] bands = p.getBands();
        for (Band band : bands) {
            final IndexCoding indexCoding = readIndexCoding(ctx, band.getName());
            if (indexCoding != null) {
                p.getIndexCodingGroup().add(indexCoding);
                band.setSampleCoding(indexCoding);
            }
        }
    }

    @Override
    public void define(ProfileWriteContext ctx, Product p) throws IOException {
        final Band[] bands = p.getBands();
        NetcdfFileWriteable writeable = ctx.getNetcdfFileWriteable();
        for (Band band : bands) {
            IndexCoding indexCoding = band.getIndexCoding();
            if (indexCoding != null) {
                Variable variable = writeable.findVariable(band.getName());
                writeIndexCoding(indexCoding, variable);
            }
        }
    }

    private void writeIndexCoding(IndexCoding indexCoding, Variable variable) {
        CfIndexCodingPart.writeIndexCoding(indexCoding, variable);
        final String[] indexNames = indexCoding.getIndexNames();
        final StringBuffer descriptions = new StringBuffer();
        for (String indexName : indexNames) {
            final MetadataAttribute index = indexCoding.getIndex(indexName);
            if (index != null) {
                final String description = index.getDescription();
                if (description != null) {
                    descriptions.append(description);
                }
            }
            descriptions.append(DESCRIPTION_SEPARATOR);
        }
        variable.addAttribute(new Attribute(INDEX_DESCRIPTIONS, descriptions.toString().trim()));
        variable.addAttribute(new Attribute(INDEX_CODING_NAME, indexCoding.getName()));
    }

    private static IndexCoding readIndexCoding(ProfileReadContext ctx, String bandName) throws ProductIOException {
        final IndexCoding indexCoding = CfIndexCodingPart.readIndexCoding(ctx, bandName);

        if (indexCoding != null) {
            final Variable variable = ctx.getGlobalVariablesMap().get(bandName);

            final Attribute descriptionsAtt = variable.findAttributeIgnoreCase(INDEX_DESCRIPTIONS);
            if (descriptionsAtt != null) {
                final String[] descriptions = descriptionsAtt.getStringValue().split(DESCRIPTION_SEPARATOR);
                if (indexCoding.getNumAttributes() == descriptions.length) {
                    for (int i = 0; i < descriptions.length; i++) {
                        indexCoding.getAttributeAt(i).setDescription(descriptions[i]);
                    }
                }
            }

            final Attribute nameAtt = variable.findAttributeIgnoreCase(INDEX_CODING_NAME);
            if (nameAtt != null) {
                indexCoding.setName(nameAtt.getStringValue());
            }
        }

        return indexCoding;
    }
}