package com.cliffc.aa.view;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.TestParse;
import com.cliffc.aa.TypeEnv;
import com.cliffc.aa.node.ConNode;
import com.cliffc.aa.node.Node;
import com.cliffc.aa.node.ScopeNode;
import com.cliffc.aa.type.TypeInt;
import com.cliffc.aa.type.TypeMem;
import com.google.common.collect.Iterables;
import jcog.Util;
import jcog.data.graph.MapNodeGraph;
import spacegraph.space2d.container.graph.Graph2D;
import spacegraph.space2d.container.graph.NodeGraphRenderer;
import spacegraph.space2d.container.graph.NodeVis;
import spacegraph.space2d.container.layout.ForceDirected2D;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static spacegraph.SpaceGraph.window;

public class ViewNodes {

    final Graph2D<Node> v = new Graph2D<>();
    final MapNodeGraph<Node,Object> g = new MapNodeGraph<>();


    private final Graph2D.Graph2DRenderer<Node> SCALE_BY_USES = (v, edit) -> {
        v.colorHash();
        v.pri = Util.sqrt(1 + v.id._uses.len());
    };

    private final Graph2D.Graph2DRenderer<Node> USES = (v, edit) -> {
        float w = 1f / (1+ v.id._uses.len());
        v.id._uses.forEach(u -> {
            edit.edge(v, u).color(1-w, w, 0, 0.5f).weight(w);
        });
    };
    private final Graph2D.Graph2DRenderer<Node> DEFS = (v, edit) -> {
        v.id._defs.forEach(u -> {
            if (u!=null)
                edit.edge(v, u).color(0, 1, 0, 0.25f).weight(0.25f);
        });
    };

    public ViewNodes(Node nodes) {
        this(List.of(nodes));
    }

    public ViewNodes(Iterable<Node> nodes) {

        nodes.forEach(this::addNode);

        //g.print();

        window(v.update(new ForceDirected2D<>())
                .set(g.nodeIDs()) //HACK fix the api
                .render(new NodeGraphRenderer<>(), USES/*, DEFS*/, SCALE_BY_USES)
                .set(g.nodeIDs())
                .widget(),
                800, 800);
    }

    private void addNode(Node n) {
        if (n == null) return;

        if (g.addNewNode(n)) {
            n._uses.forEach(this::addNode);
            n._defs.forEach(this::addNode);
        }
    }

    public static void main(String[] args) {
        new ViewNodes(TestParse.run(
            "fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(3)"
        )._env._scope);
    }
}
