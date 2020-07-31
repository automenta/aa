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
import com.cliffc.aa.view.layout.Port;
import com.cliffc.aa.view.layout.Vertex;
import jcog.math.v2;

/**
 *
 * @author Thomas Wuerthinger
 */
public class ClusterOutputSlotNode implements Vertex {

    private final int SIZE = 0;
    private v2 position;
    private final Port inputSlot;
    private final Port outputSlot;
    private final ClusterNode blockNode;
    private boolean root;
    private Cluster cluster;
    private ClusterOutgoingConnection conn;
    private final String id;

    public void setOutgoingConnection(ClusterOutgoingConnection c) {
        this.conn = c;
    }

    public ClusterOutgoingConnection getOutgoingConnection() {
        return conn;
    }

    @Override
    public String toString() {
        return id;
    }

    public ClusterOutputSlotNode(ClusterNode n, String id) {
        this.blockNode = n;
        this.id = id;

        n.addSubNode(this);

        final Vertex thisNode = this;

        inputSlot = new Port() {

            public v2 posRel() {
                return new v2(0, 0);
            }

            public Vertex v() {
                return thisNode;
            }

            @Override
            public String toString() {
                return "InPort of " + thisNode.toString();
            }
        };

        outputSlot = new Port() {

            public v2 posRel() {
                v2 p = new v2(thisNode.pos());
                p.x += ClusterNode.BORDER;
                p.y = 0;//thisBlockNode.getSize().height;
                return p;
            }

            public Vertex v() {
                return blockNode;
            }

            @Override
            public String toString() {
                return "OutPort of " + thisNode.toString();
            }
        };
    }

    public v2 size() {
        return new v2(SIZE, SIZE);
    }

    public void pos(v2 p) {
        this.position = p;
    }

    public v2 pos() {
        return position;
    }

    public Port getInputSlot() {
        return inputSlot;
    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public boolean isRoot() {
        return root;
    }

    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }
}
