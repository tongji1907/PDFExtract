/*
 * Copyright 2010 Øyvind Berg (elacin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elacin.pdfextract.physical.segmentation;

import org.apache.log4j.Logger;
import org.elacin.pdfextract.content.PhysicalPage;
import org.elacin.pdfextract.content.PhysicalPageRegion;
import org.elacin.pdfextract.content.WhitespaceRectangle;
import org.elacin.pdfextract.physical.segmentation.paragraph.ParagraphSegmentatorWS;
import org.elacin.pdfextract.tree.LayoutRegionNode;
import org.elacin.pdfextract.tree.PageNode;
import org.elacin.pdfextract.tree.ParagraphNode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: elacin
 * Date: 16.01.11
 * Time: 19.42
 * To change this template use File | Settings | File Templates.
 */
public class TreeCreator {
// ------------------------------ FIELDS ------------------------------

private static final Logger log = Logger.getLogger(TreeCreator.class);

// -------------------------- PUBLIC STATIC METHODS --------------------------

@NotNull
public static PageNode compileLogicalPage(PhysicalPage page) {
    /* first create the page node which will hold everything */
    PageNode ret = new PageNode(page.getPageNumber());

    /* then create region nodes */
    List<LayoutRegionNode> regions = createRegionNodes(page);
    for (LayoutRegionNode regionNode : regions) {
        ret.addChild(regionNode);
    }
    if (log.isInfoEnabled()) {
        log.info("LOG00940:Page had " + regions.size() + " regions");
    }

    /* this is all just rendering information */
    final List<WhitespaceRectangle> whitespaces = new ArrayList<WhitespaceRectangle>();
    addWhiteSpaceFromRegion(whitespaces, page.getMainRegion());
    ret.addDebugFeatures(Color.CYAN, page.getAllGraphics());
    ret.addDebugFeatures(Color.GREEN, whitespaces);

    return ret;
}

@NotNull
private static List<LayoutRegionNode> createRegionNodes(PhysicalPage page) {
    List<LayoutRegionNode> ret = new ArrayList<LayoutRegionNode>();

    final PhysicalPageRegion mainRegion = page.getMainRegion();

    LayoutRegionNode regionNode = new LayoutRegionNode(false);

    List<ParagraphNode> paragraphs = createParagraphNodes(mainRegion);
    for (ParagraphNode paragraph : paragraphs) {
        regionNode.addChild(paragraph);
    }
    if (!regionNode.getChildren().isEmpty()) {
        ret.add(regionNode);
    }


    for (PhysicalPageRegion subRegion : mainRegion.getSubregions()) {
        ret.add(createRegionNode(subRegion));
    }


    return ret;
}

@NotNull
private static List<ParagraphNode> createParagraphNodes(PhysicalPageRegion regionNode) {
    ParagraphSegmentatorWS paragraphSegmentator = new ParagraphSegmentatorWS(regionNode);
    return paragraphSegmentator.createParagraphNodes();
}

@NotNull
private static LayoutRegionNode createRegionNode(PhysicalPageRegion region) {
    LayoutRegionNode regionNode = new LayoutRegionNode(region.isGraphicalRegion());

    List<ParagraphNode> paragraphs = createParagraphNodes(region);
    for (ParagraphNode paragraph : paragraphs) {
        regionNode.addChild(paragraph);
    }

    for (PhysicalPageRegion subRegion : region.getSubregions()) {
        final LayoutRegionNode subRegionNode = createRegionNode(subRegion);
        if (!subRegionNode.getChildren().isEmpty()) {
            regionNode.addChild(subRegionNode);
        }
    }

    return regionNode;
}

private static void addWhiteSpaceFromRegion(List<WhitespaceRectangle> whitespaces,
                                            PhysicalPageRegion region) {
    whitespaces.addAll(region.getWhitespace());
    for (PhysicalPageRegion subRegion : region.getSubregions()) {
        addWhiteSpaceFromRegion(whitespaces, subRegion);
    }
}
}