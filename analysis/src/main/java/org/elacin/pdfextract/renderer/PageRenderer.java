/*
 * Copyright 2010 Øyvind Berg (elacin@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.elacin.pdfextract.renderer;

import org.apache.log4j.Logger;
import org.elacin.pdfextract.Constants;
import org.elacin.pdfextract.content.GraphicContent;
import org.elacin.pdfextract.content.WhitespaceRectangle;
import org.elacin.pdfextract.geom.HasPosition;
import org.elacin.pdfextract.geom.Rectangle;
import org.elacin.pdfextract.integration.DocumentContent;
import org.elacin.pdfextract.integration.PDFSource;
import org.elacin.pdfextract.integration.PageContent;
import org.elacin.pdfextract.integration.RenderedPage;
import org.elacin.pdfextract.tree.*;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.elacin.pdfextract.Constants.RENDER_DPI;

/**
 * Created by IntelliJ IDEA. User: elacin Date: Jun 17, 2010 Time: 5:02:09 AM To change this
 * template use File | Settings | File Templates.
 */
public class PageRenderer {
// ------------------------------ FIELDS ------------------------------

private static final Logger log = Logger.getLogger(PageRenderer.class);

@NotNull
private static final Color TRANSPARENT_WHITE = new Color(255, 255, 255, 0);
private static final Color DONT_DRAW         = new Color(254, 254, 254, 0);

public final  PDFSource    source;
private final int          resolution;
private final DocumentNode documentNode;


private Graphics2D graphics;
private float      xScale;
private float      yScale;

// --------------------------- CONSTRUCTORS ---------------------------

public PageRenderer(final PDFSource source, final DocumentNode documentNode, final int resolution) {
    this.source = source;
    this.documentNode = documentNode;
    this.resolution = resolution;
}

// -------------------------- STATIC METHODS --------------------------

static Color getColorForObject(Object o) {
    if (o.getClass().equals(WhitespaceRectangle.class)) {
        if (((WhitespaceRectangle) o).getScore() == 1000) {
            return Color.RED;
        }
        if (Constants.RENDER_WHITESPACE) {
            return Color.BLACK;
        } else {
            return DONT_DRAW;
        }
    } else if (o.getClass().equals(GraphicContent.class)) {
        return Color.MAGENTA;
    } else if (o.getClass().equals(LayoutRegionNode.class)) {
        if (((LayoutRegionNode) o).isPictureRegion()) {
            return Color.MAGENTA;
        }
        return Color.PINK;
    } else if (o.getClass().equals(ParagraphNode.class)) {
        return Color.YELLOW;
    } else if (o.getClass().equals(LineNode.class)) {
        return Color.BLUE;
    } else if (o.getClass().equals(WordNode.class)) {
        return Color.ORANGE;
    } else {
        return DONT_DRAW;
    }
}

// -------------------------- PUBLIC METHODS --------------------------

@NotNull
public BufferedImage renderToFile(final int pageNum, File outputFile) {
    final PageNode pageNode = documentNode.getPageNumber(pageNum);

    if (pageNode == null) {
        throw new RuntimeException("Renderer: No contents found for page " + pageNum + ".");
    }
    long t0 = System.currentTimeMillis();

    /* first have PDFBox draw the pdf to a BufferedImage */
    final BufferedImage image;
    if (Constants.RENDER_REAL_PAGE) {
        final RenderedPage renderedPage = source.renderPage(pageNum);
        image = renderedPage.getRendering();
        xScale = renderedPage.getXScale();
        yScale = renderedPage.getYScale();
    } else {
        Rectangle dims = null;
        final DocumentContent docContent = source.readPages();
        for (PageContent page : docContent.getPages()) {
            if (page.getPageNum() == pageNum) {
                dims = page.getDimensions();
            }
        }
        assert dims != null;


        float scaling = resolution / (float) RENDER_DPI;

        int widthPx = Math.round(dims.getWidth() * scaling);
        int heightPx = Math.round(dims.getHeight() * scaling);

        BufferedImage ret = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) ret.getGraphics();
        g.setBackground(TRANSPARENT_WHITE);
        g.clearRect(0, 0, ret.getWidth(), ret.getHeight());
        g.scale(scaling, scaling);

        image = ret;
        //        yScale = heightPx / pageNode.getPos().getHeight();
        yScale = scaling;
        xScale = scaling;
        //        xScale = widthPx / pageNode.getPos().getWidth();
        //        xScale = 1.0f;
        //        yScale = 1.0f;
    }


    /* then draw our information on top */
    graphics = image.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


    for (Map.Entry<Color, List<HasPosition>> o : pageNode.getDebugFeatures().entrySet()) {
        for (HasPosition position : o.getValue()) {
            if (position instanceof GraphicContent) {
                final GraphicContent graphicContent = (GraphicContent) position;
                if (!graphicContent.isBackgroundColor()) {
                    drawRectangle(position);
                }
            }
        }
    }


    drawTree(pageNode);

    for (Map.Entry<Color, List<HasPosition>> o : pageNode.getDebugFeatures().entrySet()) {
        for (HasPosition position : o.getValue()) {
            if ((position instanceof WhitespaceRectangle)) {
                drawRectangle(position);
            }
        }
    }


    /* write to file */
    try {
        ImageIO.write(image, "png", outputFile);
    } catch (IOException e) {
        log.warn("Error while writing rendered image to file", e);
    }

    log.info(String.format("LOG00180:Rendered page %d in %d ms", pageNum,
            System.currentTimeMillis() - t0));

    return image;
}

// -------------------------- OTHER METHODS --------------------------

@SuppressWarnings({"NumericCastThatLosesPrecision"})
private void drawRectangle(@NotNull final HasPosition object) {
    final int ALPHA = 60;

    final Rectangle pos = object.getPos();

    final Color color = getColorForObject(object);

    if (DONT_DRAW.equals(color)) {
        return;
    }

    graphics.setColor(color);
    final int x = (int) ((float) pos.getX() * xScale);
    final int width = (int) ((float) pos.getWidth() * xScale);
    int y = (int) ((float) pos.getY() * yScale);
    final int height = (int) ((float) pos.getHeight() * yScale);

    graphics.drawRect(x, y, width, height);
    if (true) {
        graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA));
        graphics.fillRect(x, y, width, height);
    }
}

private void drawTree(AbstractParentNode parent) {
    //    if (!(parent instanceof AbstractParentNode)) {
    drawRectangle(parent);
    //    }

    for (Object o : parent.getChildren()) {
        if (o instanceof AbstractParentNode) {
            drawTree((AbstractParentNode) o);
        } else {
            drawRectangle((HasPosition) o);
        }
    }
}
}
