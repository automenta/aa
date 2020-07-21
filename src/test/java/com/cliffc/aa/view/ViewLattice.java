package com.cliffc.aa.view;

import com.cliffc.aa.TestLattice;
import jcog.data.graph.MapNodeGraph;
import jcog.data.graph.Node;
import spacegraph.space2d.container.graph.Graph2D;
import spacegraph.space2d.container.graph.NodeGraphRenderer;
import spacegraph.space2d.container.layout.ForceDirected2D;

import java.util.BitSet;

import static spacegraph.SpaceGraph.window;

public class ViewLattice {

    public static void main(String[] args) {
        /* from: testLattice0 */
        TestLattice.N.reset();
        TestLattice.N scal= new TestLattice.N( "scalar");
        TestLattice.N num = new TestLattice.N("num" ,scal);
        TestLattice.N int8= new TestLattice.N("int8",num );

        TestLattice.N oop0= new TestLattice.N( "oop?",scal);
        TestLattice.N str0= new TestLattice.N( "str?",oop0);
        TestLattice.N tup0= new TestLattice.N( "tup?",oop0);
        TestLattice.N str = new TestLattice.N( "str" ,str0);
        TestLattice.N tup = new TestLattice.N( "tup" ,tup0);

        TestLattice.N nil = new TestLattice.N( "0",str0,tup0,int8);

        TestLattice.N abc = new TestLattice.N( "\"abc\"",str);
        TestLattice.N def = new TestLattice.N( "\"def\"",str);
        TestLattice.N flx = new TestLattice.N( "@{x}"   ,tup);
        TestLattice.N fly = new TestLattice.N( "@{y}"   ,tup);
        TestLattice.N strx= new TestLattice.N("~str" ,abc,def);
        TestLattice.N tupx= new TestLattice.N("~tup" ,flx,fly);
        TestLattice.N str_= new TestLattice.N("~str+0",strx,nil);
        TestLattice.N tup_= new TestLattice.N("~tup+0",tupx,nil);
        TestLattice.N oop_= new TestLattice.N("~oop+0",str_,tup_);

        TestLattice.N i3  = new TestLattice.N("42",int8 );
        TestLattice.N xint= new TestLattice.N("~int8",i3,nil );
        TestLattice.N xnum= new TestLattice.N("~num",xint);

        TestLattice.N xscl= new TestLattice.N( "~scalar",oop_,xnum);
        // Mark the non-centerline duals
        scal.set_dual(xscl);
        num .set_dual(xnum);
        int8.set_dual(xint);
        oop0.set_dual(oop_);
        str0.set_dual(str_);
        tup0.set_dual(tup_);
        str .set_dual(strx);
        tup .set_dual(tupx);

        xscl.walk_set_sup(new BitSet()); // Fill in the reverse edges

        MapNodeGraph<Object,Object> g = new MapNodeGraph<>();
        xscl.walk((parent,n) -> g.addEdge(n, "->", parent));

        window(new Graph2D<Node<Object, Object>>()
                    .update(new ForceDirected2D<>())
                    .render(new NodeGraphRenderer<>())
                    .set(g)
                    .widget(),
                800, 800);

    }

}
