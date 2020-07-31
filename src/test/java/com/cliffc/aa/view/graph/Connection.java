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

import com.cliffc.aa.view.data.Source;
import com.cliffc.aa.view.layout.Link;
import com.cliffc.aa.view.layout.Port;
import jcog.math.v2;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Connection implements Source.Provider, Link {

    @Override
    public boolean isVIP() {
        return style == ConnectionStyle.BOLD;
    }

    public enum ConnectionStyle {

        NORMAL,
        DASHED,
        BOLD
    }
    private final InputSlot inputSlot;
    private final OutputSlot outputSlot;
    private final Source source;
    private Color color;
    private ConnectionStyle style;
    private List<v2> controlPoints;
    private final String label;
    private final String type;

    Connection(InputSlot inputSlot, OutputSlot outputSlot, String label, String type) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.label = label;
        this.type = type;
        this.inputSlot.connections.add(this);
        this.outputSlot.connections.add(this);
        controlPoints = new ArrayList<>();
        Figure sourceFigure = this.outputSlot.getFigure();
        Figure destFigure = this.inputSlot.getFigure();
        sourceFigure.addSuccessor(destFigure);
        destFigure.addPredecessor(sourceFigure);
        source = new Source();

        this.color = Color.BLACK;
        this.style = ConnectionStyle.NORMAL;
    }

    private InputSlot getInputSlot() {
        return inputSlot;
    }

    private OutputSlot getOutputSlot() {
        return outputSlot;
    }

    public Color getColor() {
        return color;
    }

    public ConnectionStyle getStyle() {
        return style;
    }

    public void setColor(Color c) {
        color = c;
    }

    public void setStyle(ConnectionStyle s) {
        style = s;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public void remove() {
        inputSlot.getFigure().removePredecessor(outputSlot.getFigure());
        inputSlot.connections.remove(this);
        outputSlot.getFigure().removeSuccessor(inputSlot.getFigure());
        outputSlot.connections.remove(this);
    }

    public String getToolTipText() {
        StringBuilder builder = new StringBuilder();
        if (label != null) {
            builder.append(label).append(": ");
        }
        if (type != null) {
            builder.append(type).append(" ");
        }
        builder.append("from ");
        builder.append(getOutputSlot().getFigure().getSource().getSourceNodes().get(0).getId());
        builder.append(" to ");
        builder.append(getInputSlot().getFigure().getSource().getSourceNodes().get(0).getId());
        return builder.toString();
    }

    @Override
    public String toString() {
        return "Connection('" + label + "', " + getFrom().v() + " to " + getTo().v() + ")";
    }

    @Override
    public Port getFrom() {
        return outputSlot;
    }

    @Override
    public Port getTo() {
        return inputSlot;
    }

    @Override
    public List<v2> getControlPoints() {
        return controlPoints;
    }

    @Override
    public void setControlPoints(List<v2> list) {
        controlPoints = list;
    }
}

