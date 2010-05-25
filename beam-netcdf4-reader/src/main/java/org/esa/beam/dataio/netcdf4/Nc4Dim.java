package org.esa.beam.dataio.netcdf4;

import ucar.nc2.Dimension;
import ucar.nc2.Variable;

import java.util.Arrays;

/**
 * Represents a NC dimensions.
 *
 * @author Sabine Embacher
 */
public class Nc4Dim {

    private final static int dimIdxX = 0;
    private final static int dimIdyY = 1;
    private final Dimension[] dims;

    public Nc4Dim(Dimension[] dims) {
        assert dims != null;
        assert dims.length >= 1;
        for (Dimension dim : dims) {
            assert dim != null;
        }
        this.dims = new Dimension[dims.length];
        System.arraycopy(dims, 0, this.dims, 0, dims.length);
    }

    public Dimension getDimX() {
        return dims[dimIdxX];
    }

    public Dimension getDimY() {
        return dims[dimIdyY];
    }

    public boolean is2D() {
        return dims.length == 2;
    }

    public boolean isTypicalRasterDim() {
        return is2D() && (matchesXYDimNames("lon", "lat") ||
                          matchesXYDimNames("longitude", "latitude") ||
                          matchesXYDimNames("ni", "nj") ||
                          matchesXYDimNames("x", "y"));
    }

    // Move to GeocodingUtils

    public boolean fitsTo(final Variable varX, final Variable varY) {
        return varX.getRank() == 1 &&
               varY.getRank() == 1 &&
               varX.getDimension(0).getLength() == getDimX().getLength() &&
               varY.getDimension(0).getLength() == getDimY().getLength();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Nc4Dim) {
            final Nc4Dim other = (Nc4Dim) obj;
            return Arrays.equals(dims, other.dims);
        }
        return false;
    }


    @Override
    public int hashCode() {
        int hash = 0;
        for (Dimension dim : dims) {
            hash += dim.hashCode();
        }
        return hash;
    }

    private boolean matchesXYDimNames(final String xName, final String yName) {
        return getDimX().getName().equalsIgnoreCase(xName)
               && getDimY().getName().equalsIgnoreCase(yName);
    }
}