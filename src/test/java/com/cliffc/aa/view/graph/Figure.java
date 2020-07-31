/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.cliffc.aa.view.graph;

//import com.cliffc.aa.view.InputBlock;
//import com.cliffc.aa.view.InputGraph;
//import com.cliffc.aa.view.InputNode;
//import com.cliffc.aa.view.Source;
//import com.cliffc.aa.view.layout.Cluster;
//import com.cliffc.aa.view.layout.Vertex;

import com.cliffc.aa.view.data.Properties;
import com.cliffc.aa.view.data.*;
import com.cliffc.aa.view.layout.Cluster;
import com.cliffc.aa.view.layout.Vertex;
import jcog.math.v2;
import jcog.tree.rtree.rect.RectFloat;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;


public class Figure extends Entity implements Source.Provider, Vertex {

    private static final int INSET = 8;
    public static final int SLOT_WIDTH = 10;
    private static final int OVERLAPPING = 6;
    public static final int SLOT_START = 4;
    private static final int SLOT_OFFSET = 8;
    private static final boolean VERTICAL_LAYOUT = true;
    final List<InputSlot> inputSlots;
    final List<OutputSlot> outputSlots;
    private final Source source;
    private final Diagram diagram;
    private v2 position;
    private final List<Figure> predecessors;
    private final List<Figure> successors;
    private List<InputGraph> subgraphs;
    private Color color;
    private final int id;
    private final String idString;
    private String[] lines;
    private int heightCash = -1;
    private int widthCash = -1;

    private int getHeight() {
        if (heightCash == -1) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            String nodeText = diagram.getNodeText();
            heightCash = nodeText.split("\n").length * metrics.getHeight() + INSET;
        }
        return heightCash;
    }

    public static <T> List<T> getAllBefore(List<T> inputList, T tIn) {
        List<T> result = new ArrayList<>();
        for(T t : inputList) {
            if(t.equals(tIn)) {
                break;
            }
            result.add(t);
        }
        return result;
    }

    public static int getSlotsWidth(Collection<? extends Slot> slots) {
        int result = Figure.SLOT_OFFSET;
        for(Slot s : slots) {
            result += s.width() + Figure.SLOT_OFFSET;
        }
        return result;
    }

    public int getWidth() {
        if (widthCash == -1) {
            int max = 0;
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            for (String s : getLines()) {
                int cur = metrics.stringWidth(s);
                if (cur > max) {
                    max = cur;
                }
            }
            widthCash = max + INSET;
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(inputSlots));
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(outputSlots));
        }
        return widthCash;
    }

    Figure(Diagram diagram, int id) {
        this.diagram = diagram;
        this.source = new Source();
        inputSlots = new ArrayList<>(5);
        outputSlots = new ArrayList<>(1);
        predecessors = new ArrayList<>(6);
        successors = new ArrayList<>(6);
        this.id = id;
        idString = Integer.toString(id);

        this.position = new v2(0, 0);
        this.color = Color.WHITE;
    }

    public int getId() {
        return id;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        Set<Figure> result = new HashSet<>(getPredecessors());
        return Collections.unmodifiableSet(result);
    }

    public Set<Figure> getSuccessorSet() {
        Set<Figure> result = new HashSet<>(getSuccessors());
        return Collections.unmodifiableSet(result);
    }

    public List<Figure> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    void addPredecessor(Figure f) {
        this.predecessors.add(f);
    }

    void addSuccessor(Figure f) {
        this.successors.add(f);
    }

    void removePredecessor(Figure f) {
        assert predecessors.contains(f);
        predecessors.remove(f);
    }

    void removeSuccessor(Figure f) {
        assert successors.contains(f);
        successors.remove(f);
    }

    public List<InputGraph> getSubgraphs() {
        return subgraphs;
    }

    public void setSubgraphs(List<InputGraph> subgraphs) {
        this.subgraphs = subgraphs;
    }

    @Override
    public void pos(v2 p) {
        this.position = p;
    }

    @Override
    public v2 pos() {
        return position;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public InputSlot createInputSlot() {
        InputSlot slot = new InputSlot(this, -1);
        inputSlots.add(slot);
        return slot;
    }

    public InputSlot createInputSlot(int index) {
        InputSlot slot = new InputSlot(this, index);
        inputSlots.add(slot);
        inputSlots.sort(Slot.slotIndexComparator);
        return slot;
    }

    public void removeSlot(Slot s) {

        assert inputSlots.contains(s) || outputSlots.contains(s);

        List<Connection> connections = new ArrayList<>(s.getConnections());
        for (Connection c : connections) {
            c.remove();
        }

        if (inputSlots.contains(s)) {
            inputSlots.remove(s);
        } else outputSlots.remove(s);
    }

    public OutputSlot createOutputSlot() {
        OutputSlot slot = new OutputSlot(this, -1);
        outputSlots.add(slot);
        return slot;
    }

    public OutputSlot createOutputSlot(int index) {
        OutputSlot slot = new OutputSlot(this, index);
        outputSlots.add(slot);
        outputSlots.sort(Slot.slotIndexComparator);
        return slot;
    }

    public List<InputSlot> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public Set<Slot> getSlots() {
        Set<Slot> result = new HashSet<>();
        result.addAll(getInputSlots());
        result.addAll(getOutputSlots());
        return result;
    }

    public List<OutputSlot> getOutputSlots() {
        return Collections.unmodifiableList(outputSlots);
    }

    void removeInputSlot(InputSlot s) {
        s.removeAllConnections();
        inputSlots.remove(s);
    }

    void removeOutputSlot(OutputSlot s) {
        s.removeAllConnections();
        outputSlots.remove(s);
    }

    private String[] getLines() {
        if (lines == null) {
            updateLines();
        }
        return lines;
    }

    private void updateLines() {
        String[] strings = diagram.getNodeText().split("\n");
        String[] result = new String[strings.length];

        for (int i = 0; i < strings.length; i++) {
            result[i] = resolveString(strings[i], getProperties());
        }

        lines = result;
    }

    private static String resolveString(String string, Properties properties) {

        StringBuilder sb = new StringBuilder();
        boolean inBrackets = false;
        StringBuilder curIdent = new StringBuilder();

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (inBrackets) {
                if (c == ']') {
                    String value = properties.get(curIdent.toString());
                    if (value == null) {
                        value = "";
                    }
                    sb.append(value);
                    inBrackets = false;
                } else {
                    curIdent.append(c);
                }
            } else {
                if (c == '[') {
                    inBrackets = true;
                    curIdent = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    @Override
    public v2 size() {
        if (VERTICAL_LAYOUT) {
            int width = Math.max(getWidth(), Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), outputSlots.size()) + 1));
            int height = getHeight() + 2 * Figure.SLOT_WIDTH - 2 * Figure.OVERLAPPING;


            return new v2(width, height);
        } else {
            int width = getWidth() + 2 * Figure.SLOT_WIDTH - 2*Figure.OVERLAPPING;
            int height = Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), outputSlots.size()) + 1);
            return new v2(width, height);
        }
    }

    @Override
    public String toString() {
        return idString;
    }

    public Cluster getCluster() {
        if (getSource().getSourceNodes().isEmpty()) {
            assert false : "Should never reach here, every figure must have at least one source node!";
            return null;
        } else {
            final InputBlock inputBlock = diagram.getGraph().getBlock(getSource().getSourceNodes().get(0));
            assert inputBlock != null;
            Cluster result = diagram.getBlock(inputBlock);
            assert result != null;
            return result;
        }
    }

    @Override
    public boolean isRoot() {

        List<InputNode> sourceNodes = source.getSourceNodes();
        if (!sourceNodes.isEmpty() && sourceNodes.get(0).getProperties().get("name") != null) {
            return source.getSourceNodes().get(0).getProperties().get("name").equals("Root");
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }

    public RectFloat bounds() {
        final v2 p = this.pos();
        return RectFloat.XYWH(p.x, p.y, this.getWidth(), this.getHeight());
    }
}
