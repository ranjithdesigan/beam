/*
 * Copyright (C) 2009 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.gpf.std.reproject;


import org.esa.beam.framework.datamodel.ImageGeometry;
import org.geotools.factory.Hints;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.referencing.operation.AbstractCoordinateOperationFactory;
import org.geotools.referencing.operation.matrix.XAffineTransform;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.referencing.operation.transform.DimensionFilter;
import org.geotools.referencing.operation.transform.WarpTransform2D;
import org.geotools.resources.XArray;
import org.geotools.resources.i18n.ErrorKeys;
import org.geotools.resources.i18n.Errors;
import org.geotools.resources.image.ImageUtilities;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.MathTransformFactory;
import org.opengis.referencing.operation.TransformException;

import javax.media.jai.BorderExtender;
import javax.media.jai.BorderExtenderConstant;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.OpImage;
import javax.media.jai.Warp;
import javax.media.jai.WarpAffine;
import javax.media.jai.operator.MosaicDescriptor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;


/**
 * Implementation of the {@link Resample} operation. This implementation is provided as a
 * separated class for two purpose: avoid loading this code before needed and provide some
 * way to check if a grid coverages is a result of a resample operation.
 *
 */
final class Reproject {
    

    private static final int DIMENSION_X_INDEX = 0;
    private static final int DIMENSION_Y_INDEX = 1;
    /**
     * Small tolerance threshold for floating point number comparaisons.
     */
    private static final double EPS = 1E-6;
    
    private OpImage[] leveledWarpImages;
    
    public Reproject(int numLevels) {
        leveledWarpImages = new OpImage[numLevels];
    }
    private synchronized Warp getCachingWarp(Warp warp, int width, int height, Dimension tileSize, int level) {
        if (leveledWarpImages[level] == null) {
            leveledWarpImages[level] = new WarpSourceCoordinatesOpImage(warp, width, height, tileSize, null);
        }
        return new WarpFromSourceCoordinates(leveledWarpImages[level]);
    }


    /**
     * Creates a new coverage with a different coordinate reference reference system. If a
     * grid geometry is supplied, only its {@linkplain GridGeometry2D#getRange grid range}
     * and {@linkplain GridGeometry2D#getGridToCRS grid to CRS} transform are taken in account.
     *
     * @param sourceImage
     *          The source grid coverage.
     * @param targetCRS
     *          Coordinate reference system for the new grid coverage, or {@code null}.
     * @param targetGeometry
     *          The target grid geometry, or {@code null} for default.
     * @param backgroundValue no-data-value 
     * @param interpolation
     *          The interpolation to use, or {@code null} if none.
     * @param hints
     *          The rendering hints. This is usually provided by {@link AbstractProcessor}.
     *          This method will looks for {@link Hints#COORDINATE_OPERATION_FACTORY} and
     *          {@link Hints#JAI_INSTANCE} keys.
     * @param targetLevel 
     * @return
     *          The new grid coverage, or {@code sourceCoverage} if no resampling was needed.
     * @throws FactoryException
     *          if a transformation step can't be created.
     * @throws TransformException
     *          if a transformation failed.
     */
    public RenderedImage reproject(RenderedImage sourceImage, 
                                          ImageGeometry sourceGeometry,
                                          ImageGeometry targetGeometry,
                                          double backgroundValue,
                                          final Interpolation interpolation, 
                                          final Hints hints, int targetLevel, Dimension tileSize) throws FactoryException, TransformException {

        ////////////////////////////////////////////////////////////////////////////////////////
        ////                                                                                ////
        ////            STEP 1: Extracts needed informations from the parameters            ////
        //// =======>>  STEP 2: Creates the "target to source" MathTransform       <<====== ////
        ////            STEP 3: Computes the target image layout                            ////
        ////            STEP 4: Applies the JAI operation ("Affine", "Warp", etc)           ////
        ////                                                                                ////
        ////////////////////////////////////////////////////////////////////////////////////////

        final CoordinateOperationFactory factory =
                ReferencingFactoryFinder.getCoordinateOperationFactory(hints);
        final MathTransformFactory mtFactory;
        if (factory instanceof AbstractCoordinateOperationFactory) {
            mtFactory = ((AbstractCoordinateOperationFactory) factory).getMathTransformFactory();
        } else {
            mtFactory = ReferencingFactoryFinder.getMathTransformFactory(hints);
        }
        /*
         * Computes the INVERSE of the math transform from [Source Grid] to [Target Grid].
         * The transform will be computed using the following path:
         *
         *      Target Grid --> Target CRS --> Source CRS --> Source Grid
         *                   ^              ^              ^
         *                 step 1         step 2         step 3
         *
         * If source and target CRS are equal, a shorter path is used. This special
         * case is needed because 'sourceCRS' and 'targetCRS' may be null.
         *
         *      Target Grid --> Common CRS --> Source Grid
         *                   ^              ^
         *                 step 1         step 3
         */
        final MathTransform allSteps;
        MathTransform step1 = new AffineTransform2D(targetGeometry.getImage2MapTransform());
        MathTransform step3 = new AffineTransform2D(sourceGeometry.getImage2MapTransform()).inverse();
        if (CRS.equalsIgnoreMetadata(sourceGeometry.getMapCrs(), targetGeometry.getMapCrs())) {
            allSteps = mtFactory.createConcatenatedTransform(step1, step3);
        } else {
            MathTransform step2 = factory.createOperation(targetGeometry.getMapCrs(), sourceGeometry.getMapCrs()).getMathTransform();
            /*
             * Computes the final transform.
             */
            if (step1.equals(step3.inverse())) {
                allSteps = step2;
            } else {
                allSteps = mtFactory.createConcatenatedTransform(
                           mtFactory.createConcatenatedTransform(step1, step2), step3);
            }
        }
        MathTransform2D allSteps2D = toMathTransform2D(allSteps, mtFactory);

        ////////////////////////////////////////////////////////////////////////////////////////
        ////                                                                                ////
        ////            STEP 1: Extracts needed informations from the parameters            ////
        ////            STEP 2: Creates the "target to source" MathTransform                ////
        //// =======>>  STEP 3: Computes the target image layout                   <<====== ////
        ////            STEP 4: Applies the JAI operation ("Affine", "Warp", etc)           ////
        ////                                                                                ////
        ////////////////////////////////////////////////////////////////////////////////////////

        final RenderingHints targetHints = getRenderingHints(sourceImage, interpolation);
        if (hints != null) {
            targetHints.add(hints);
        }
        /*
         * Creates the border extender from the background values. We add it inconditionnaly as
         * a matter of principle, but it will be ignored by all JAI operations except "Affine".
         * There is an exception for the case where the user didn't specified explicitly the
         * desired target grid range. NOT specifying border extender will allows "Affine" to
         * shrink the target image bounds to the range containing computed values.
         */
        final double[] background = new double[]{backgroundValue};
        final BorderExtender borderExtender;
        if (XArray.allEquals(background, 0)) {
            borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_ZERO);
        } else {
            borderExtender = new BorderExtenderConstant(background);
        }
        hints.put(JAI.KEY_BORDER_EXTENDER, borderExtender);

        ////////////////////////////////////////////////////////////////////////////////////////
        ////                                                                                ////
        ////            STEP 1: Extracts needed informations from the parameters            ////
        ////            STEP 2: Creates the "target to source" MathTransform                ////
        ////            STEP 3: Computes the target image layout                            ////
        //// =======>>  STEP 4: Applies the JAI operation ("Affine", "Warp", etc)  <<====== ////
        ////                                                                                ////
        ////////////////////////////////////////////////////////////////////////////////////////
        /*
         * If the user requests a new grid geometry with the same coordinate reference system,
         * and if the grid geometry is equivalents to a simple extraction of a sub-area, then
         * delegates the work to a "Crop" operation.
         */
        final String operation;
        final ParameterBlock paramBlk = new ParameterBlock().addSource(sourceImage);
        if (allSteps.isIdentity() || (allSteps instanceof AffineTransform &&
                XAffineTransform.isIdentity((AffineTransform) allSteps, EPS))) {

            final Rectangle sourceBB = sourceGeometry.getImageRect();
            final Rectangle targetBB = targetGeometry.getImageRect();
            /*
             * Since there is no interpolation to perform, use the native view (which may be
             * packed or geophysics - it is just the view which is closest to original data).
             */
            paramBlk.removeSources();
            paramBlk.addSource(sourceImage);
            if (targetBB.equals(sourceBB)) {
                /*
                 * Optimization in case we have nothing to do, not even a crop. Reverts to the
                 * original coverage BEFORE to creates Resampler2D. Note that while there is
                 * nothing to do, the target CRS is not identical to the source CRS (so we need
                 * to create a new coverage) otherwise this condition would have been detected
                 * sooner in this method.
                 */
                return sourceImage;
            }
            if (sourceBB.contains(targetBB)) {
                operation = "Crop";
                paramBlk.add(Float.valueOf(targetBB.x))
                        .add(Float.valueOf(targetBB.y))
                        .add(Float.valueOf(targetBB.width))
                        .add(Float.valueOf(targetBB.height));
            } else {
                operation = "Mosaic";
                paramBlk.add(MosaicDescriptor.MOSAIC_TYPE_OVERLAY)
                        .add(null).add(null).add(null).add(background);
            }
        } else {
            operation = "Warp";
            Warp warp;
                if (allSteps2D instanceof AffineTransform) {
                    warp = new WarpAffine((AffineTransform) allSteps2D);
                } else {
                    warp = WarpTransform2D.getWarp(null, allSteps2D);
                }
                Rectangle imageRect = targetGeometry.getImageRect();
                warp = getCachingWarp(warp, imageRect.width, imageRect.height, tileSize, targetLevel);
            paramBlk.add(warp).add(interpolation).add(background);
        }
        return JAI.getDefaultInstance().createNS(operation, paramBlk, targetHints);
    }

    /**
     * Returns the math transform for the two specified dimensions of the specified transform.
     *
     * @param  transform The transform.
     * @param  mtFactory The factory to use for extracting the sub-transform.
     * @return The {@link MathTransform2D} part of {@code transform}.
     * @throws FactoryException If {@code transform} is not separable.
     */
    private static MathTransform2D toMathTransform2D(final MathTransform transform,
                                                     final MathTransformFactory mtFactory) throws FactoryException
    {
        final DimensionFilter filter = new DimensionFilter(mtFactory);
        filter.addSourceDimension(DIMENSION_X_INDEX);
        filter.addSourceDimension(DIMENSION_Y_INDEX);
        MathTransform candidate = filter.separate(transform);
        if (candidate instanceof MathTransform2D) {
            return (MathTransform2D) candidate;
        }
        filter.addTargetDimension(DIMENSION_X_INDEX);
        filter.addTargetDimension(DIMENSION_Y_INDEX);
        candidate = filter.separate(transform);
        if (candidate instanceof MathTransform2D) {
            return (MathTransform2D) candidate;
        }
        throw new FactoryException(Errors.format(ErrorKeys.NO_TRANSFORM2D_AVAILABLE));
    }

    private static RenderingHints getRenderingHints(final RenderedImage image, Interpolation interpolation) {
        RenderingHints hints = ImageUtilities.getRenderingHints(image);
        if (hints == null) {
            hints = new RenderingHints(null);
        }
        hints.put(JAI.KEY_INTERPOLATION, interpolation);
        return hints;
    }
}