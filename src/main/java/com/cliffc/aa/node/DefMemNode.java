package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.Type;
import com.cliffc.aa.type.TypeMem;
import com.cliffc.aa.type.TypeObj;

public class DefMemNode extends Node {
  public DefMemNode( Node ctrl, Node isuse) { super(OP_DEFMEM,ctrl,isuse); }
  @Override public Node ideal(GVNGCM gvn, int level) { return null; }
  @Override public TypeMem value(GVNGCM gvn) {
    TypeObj[] tos = new TypeObj[_defs._len];
    assert in(1)==null; // No need for a 'USE' object
    tos[1] = TypeObj.ISUSED; // Default: all objects are possibly allocated to worst possible
    for( int i=2; i<_defs._len; i++ ) {
      Node n = in(i);
      if( n==null ) continue;
      if( n instanceof MProjNode ) { // NewNode still alive
        NewNode nnn = (NewNode)n.in(0);
        tos[i] = nnn.escaped(gvn) ? nnn._crushed : TypeObj.UNUSED;
      } else {                  // Collapsed NewNode
        Type tn = gvn.type(n);
        tos[i] = tn instanceof TypeObj ? (TypeObj)tn : tn.oob(TypeObj.ISUSED);
      }
    }
    return TypeMem.make0(tos);
  }
  @Override public boolean basic_liveness() { return false; }
  @Override public TypeMem live_use( GVNGCM gvn, Node def ) { return gvn._opt_mode < 2 ? _live : TypeMem.DEAD; }
  @Override public boolean equals(Object o) { return this==o; } // Only one

  // Make an MProj for a New, and 'hook' it into the default memory
  public MProjNode make_mem_proj(GVNGCM gvn, NewNode nn) {
    MProjNode mem = (MProjNode)gvn.xform(new MProjNode(nn,0));
    return make_mem(gvn,nn,mem);
  }
  public MProjNode make_mem(GVNGCM gvn, NewNode nn, MProjNode mem) {
    TypeObj[] tos0 = TypeMem.MEM.alias2objs();
    while( _defs._len < tos0.length )
      gvn.add_def(this,gvn.con(TypeMem.MEM.at(_defs._len)));
    while( _defs._len <= nn._alias ) gvn.add_def(this,null);
    gvn.set_def_reg(this,nn._alias,mem);
    // Lift default immediately; default mem used by the Parser to load-thru displays.
    gvn.setype(this,value(gvn));
    gvn.add_work_uses(this);
    return mem;
  }
}

