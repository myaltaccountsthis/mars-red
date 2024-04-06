package mars.util;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * An icon whose image is in the Scalable Vector Graphics format.
 * Since it is a vector image, this icon can be resized, despite the {@link Icon}
 * interface being defined to represent a "small <i>fixed size</i> picture."
 */
public class SVGIcon implements Icon {
    private final SVGToBufferedImageTranscoder transcoder;
    private final TranscoderInput transcoderInput;
    // I considered storing the dimensions solely in the transcoder hints, but their hints are stored
    // as floats, and this is more convenient for the getter methods anyway
    private int width;
    private int height;

    /**
     * Create a new {@code SVGIcon} from a URI and initial dimensions.
     *
     * @param uri    The URI for the icon image.
     * @param width  The initial width of the icon.
     * @param height The initial height of the icon.
     * @throws TranscoderException Thrown if an error occurred while loading the image.
     */
    public SVGIcon(String uri, int width, int height) throws TranscoderException {
        this.transcoder = new SVGToBufferedImageTranscoder();
        this.transcoderInput = new TranscoderInput(uri);
        // This method call will generate the initial image for the icon
        this.setIconDimensions(width, height);
    }

    /**
     * Draw the icon at the specified location.
     *
     * @param component A {@code Component} to get properties useful for painting (not used).
     * @param gfx       The graphics context.
     * @param leftX     The X coordinate of the icon's top-left corner.
     * @param topY      The Y coordinate of the icon's top-left corner.
     */
    @Override
    public void paintIcon(Component component, Graphics gfx, int leftX, int topY) {
        gfx.drawImage(this.transcoder.getOutput(), leftX, topY, null);
    }

    /**
     * Get the icon's width.
     *
     * @return An int specifying the width of the icon.
     */
    @Override
    public int getIconWidth() {
        return this.width;
    }

    /**
     * Get the icon's height.
     *
     * @return An int specifying the height of the icon.
     */
    @Override
    public int getIconHeight() {
        return this.height;
    }

    /**
     * Set the icon's image dimensions. Note that this reloads the image.
     *
     * @param width  The new width of the icon.
     * @param height The new height of the icon.
     * @throws TranscoderException Thrown if an error occurred while reloading the image.
     */
    public void setIconDimensions(int width, int height) throws TranscoderException {
        this.width = width;
        this.height = height;
        // Since the width and height may have changed, generate a new image for the icon
        this.updateImage();
    }

    /**
     * Update the icon's image by transcoding the SVG again with the set dimensions.
     *
     * @throws TranscoderException Thrown if an error occurred while transcoding.
     */
    private void updateImage() throws TranscoderException {
        // Update the target width and height for the transcoder
        this.transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) width);
        this.transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) height);
        // Perform the transcode operation
        this.transcoder.transcode(transcoderInput);
    }

    /**
     * A concrete implementation of the Apache Batik {@link ImageTranscoder} which transcodes
     * an SVG image to the raw {@link BufferedImage} instead of a file format. Since {@code BufferedImage}
     * is used internally anyway, this effectively just cuts out the step involving {@link TranscoderOutput}.
     * <p>
     * This method of transcoding SVG images is derived from @Devon_C_Miller's answer
     * to <a href="https://stackoverflow.com/questions/2495501/swing-batik-create-an-imageicon-from-an-svg-file">
     * this StackOverflow question</a>. Thanks Devon!
     */
    private static class SVGToBufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage output = null;

        /**
         * Transcodes the specified input without creating any output.
         * After this operation, the resulting {@link BufferedImage} can be retrieved via {@link #getOutput()}.
         *
         * @param input The input to transcode.
         * @throws TranscoderException Thrown if an error occurred while transcoding.
         */
        public void transcode(TranscoderInput input) throws TranscoderException {
            this.transcode(input, null);
        }

        /**
         * Get the image generated by the most recent call to {@link #transcode(TranscoderInput)}.
         *
         * @return The image, or {@code null} if no previous transcode operation has been done.
         */
        public BufferedImage getOutput() {
            return this.output;
        }

        /**
         * Create a new image with the specified dimensions. There is probably no reason to call this
         * directly; it is primarily used by the superclass to begin the transcode operation.
         * @see BufferedImage#BufferedImage(int, int, int)
         *
         * @param width  The width of the image in pixels.
         * @param height The height of the image in pixels.
         * @return A blank ARGB {@code BufferedImage} with the specified width and height.
         */
        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        /**
         * <b>Do not use this method.</b> Calling it has no effect.
         * To get the {@link BufferedImage} from this transcoder, use {@link #getOutput()} instead.
         */
        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) {
            // This method will be called by the superclass, but we will ignore the output passed in,
            // instead updating our own output field (how rebellious of us)
            this.output = image;
        }
    }
}
