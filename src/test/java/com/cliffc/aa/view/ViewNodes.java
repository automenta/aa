package com.cliffc.aa.view;

import com.cliffc.aa.TestParse;
import com.cliffc.aa.TypeEnv;
import com.cliffc.aa.node.Node;
import com.cliffc.aa.type.TypeMem;
import jcog.Util;
import jcog.data.graph.MapNodeGraph;
import jcog.data.graph.MutableNode;
import jcog.data.graph.path.FromTo;
import org.jetbrains.annotations.Nullable;
import spacegraph.space2d.container.graph.EdgeVis;
import spacegraph.space2d.container.graph.Graph2D;
import spacegraph.space2d.container.graph.NodeGraphRenderer;
import spacegraph.space2d.container.graph.NodeVis;
import spacegraph.space2d.container.layout.Force2D;
import spacegraph.space2d.widget.button.PushButton;

import java.util.List;

import static spacegraph.SpaceGraph.window;

public class ViewNodes {

    private final Graph2D<MutableNode<Node,String>> v = new Graph2D<>();
    private final MapNodeGraph<Node,String> g = new MapNodeGraph<>();

    //TODO enum colorMode

    private final NodeGraphRenderer r = new NodeGraphRenderer<Node, String>() {

        @Override
        public void node(NodeVis<jcog.data.graph.Node<Node, String>> from, Graph2D.GraphEditor<jcog.data.graph.Node<Node, String>> graph) {
            super.node(from, graph);
            Node n = from.id.id();
            TypeMem t = n._live;

            from.pri = n._uses.len();

            PushButton b = new PushButton(n.xstr() /* todo abbr */).clicked(() -> System.out.println(n.dump(16)));
            b.color.hsl(t._hash*128, 0.9f, 0.2f);
//            from.colorHash(t._hash);
            from.set(b);
        }

        @Override
        protected void edge(NodeVis<jcog.data.graph.Node<Node, String>> from, FromTo<jcog.data.graph.Node<Node, String>, String> edge, @Nullable EdgeVis<jcog.data.graph.Node<Node, String>> edgeVis, jcog.data.graph.Node<Node, String> to) {
            //super.edge(from, edge, edgeVis, to);
            float w = 1f/(1+from.id.id()._uses.len());
            String e = edge.id();
            switch (e) {
                case "uses":
                    edgeVis.color(w, 1-w, 0, w).weightAddLerp(Util.sqr(w), 0.5f);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    };

    private ViewNodes(TypeEnv e) {
        this(List.of(e._env._scope, e._env._par!=null?e._env._par._scope : null));
    }
    public ViewNodes(Node nodes) {
        this(List.of(nodes));
    }

    private ViewNodes(Iterable<Node> nodes) {

        nodes.forEach(this::addNode);

        g.print();


        window(v.update(
                    new Force2D()
                    //new SemiForce2D.TreeForce2D<>()
                )
                .render(r)
                .set(g)
                .widget(),
                800, 800);
    }

    private void addNode(Node n) {
        if (n == null) return;

        if (n.is_dead()) return; //TODO

        if (g.addNewNode(n)) {
            n._uses.forEach(u -> {
                addNode(u);
                g.addEdge(n, "uses", u);
            });

            n._defs.forEach(d -> {
                if (d!=null) {
                    addNode(d);
//                    g.addEdge(n, "def", d);
                }
            });
        }
    }

    public static void main(String[] args) {
        new ViewNodes(TestParse.run(
                //"x=1"
                "fact = { x -> x <= 1 ? x : x*fact(x-1) }; fact(3)"
        ));
    }
}
