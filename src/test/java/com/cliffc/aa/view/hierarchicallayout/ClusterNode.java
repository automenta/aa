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
package com.cliffc.aa.view.hierarchicallayout;

import com.cliffc.aa.view.layout.Cluster;
import com.cliffc.aa.view.layout.Link;
import com.cliffc.aa.view.layout.Port;
import com.cliffc.aa.view.layout.Vertex;
import jcog.math.v2;

import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class ClusterNode implements Vertex {

    private Cluster cluster;
    private Port inputSlot;
    private Port outputSlot;
    private final Set<Vertex> subNodes;
    private v2 size;
    private v2 position;
    private final Set<Link> subEdges;
    private boolean dirty;
    private boolean root;
    private final String name;
    public static final int BORDER = 20;

    public ClusterNode(Cluster cluster, String name) {
        this.subNodes = new HashSet<>();
        this.subEdges = new HashSet<>();
        this.cluster = cluster;
        position = new v2(0, 0);
        this.name = name;
    }

    public void addSubNode(Vertex v) {
        subNodes.add(v);
    }

    public void addSubEdge(Link l) {
        subEdges.add(l);
    }

    public Set<Link> getSubEdges() {
        return Collections.unmodifiableSet(subEdges);
    }

    public void updateSize() {


        calculateSize();

        final ClusterNode widget = this;
        inputSlot = new Port() {

            public v2 posRel() {
                return new v2(size.x / 2, 0);
            }

            public Vertex v() {
                return widget;
            }
        };

        outputSlot = new Port() {

            public v2 posRel() {
                return new v2(size.x / 2, 0);//size.height);
            }

            public Vertex v() {
                return widget;
            }
        };
    }

    private void calculateSize() {

        if (subNodes.isEmpty()) {
            size = new v2(0, 0);
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (Vertex n : subNodes) {
            v2 p = n.pos();
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            v2 s = n.size();
            maxX = Math.max(maxX, p.x + s.x);
            maxY = Math.max(maxY, p.y + s.y);
        }

        for (Link l : subEdges) {
            List<v2> points = l.getControlPoints();
            for (v2 p : points) {
                if (p != null) {
                    minX = Math.min(minX, p.x);
                    maxX = Math.max(maxX, p.x);
                    minY = Math.min(minY, p.y);
                    maxY = Math.max(maxY, p.y);
                }
            }
        }

        size = new v2(maxX - minX, maxY - minY);

        // Normalize coordinates
        for (Vertex n : subNodes) {
            n.pos(new v2(n.pos().x - minX, n.pos().y - minY));
        }

        for (Link l : subEdges) {
            List<v2> points = new ArrayList<>(l.getControlPoints());
            for (v2 p : points) {
                p.x -= minX;
                p.y -= minY;
            }
            l.setControlPoints(points);

        }

        size.x += 2 * BORDER;
        size.y += 2 * BORDER;
    }

    public Port getInputSlot() {
        return inputSlot;

    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    public v2 size() {
        return size;
    }

    public v2 pos() {
        return position;
    }

    public void pos(v2 pos) {

        this.position = pos;
        for (Vertex n : subNodes) {
            v2 cur = new v2(n.pos());
            cur.added(pos.x + BORDER, pos.y + BORDER);
            n.pos(cur);
        }

        for (Link e : subEdges) {
            List<v2> arr = e.getControlPoints();
            ArrayList<v2> newArr = new ArrayList<>(arr.size());
            for (v2 p : arr) {
                if (p != null) {
                    v2 p2 = new v2(p);
                    p2.added(pos.x + BORDER, pos.y + BORDER);
                    newArr.add(p2);
                } else {
                    newArr.add(null);
                }
            }

            e.setControlPoints(newArr);
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setDirty(boolean b) {
        dirty = b;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    public boolean isRoot() {
        return root;
    }

    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public Set<? extends Vertex> getSubNodes() {
        return subNodes;
    }
}
