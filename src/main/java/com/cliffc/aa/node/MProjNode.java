package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;
import com.cliffc.aa.util.IBitSet;

// Proj memory
public class MProjNode extends ProjNode {
  public MProjNode( Node ifn, int idx ) { super(ifn,idx); }
  @Override
  public String xstr() { return "MProj"+_idx; }
  @Override boolean is_mem() { return true; }
  @Override public Node ideal(GVNGCM gvn, int level) {
    Node x = in(0).is_copy(gvn,_idx);
    if( x != null )
      return x == this ? gvn.con(TypeMem.ANYMEM) : x; // Happens in dead self-recursive functions
    if( in(0) instanceof CallEpiNode ) {
      Node precall = in(0).is_pure_call(); // See if memory can bypass pure calls (most primitives)
      if( precall != null && gvn.type(this)==gvn.type(precall) )
        return precall;
    }
    return null;
  }
  @Override IBitSet escapees(GVNGCM gvn) { return in(0).escapees(gvn); }
  @Override public boolean basic_liveness() { return false; }
  // Only called here if alive, and input is more-than-basic-alive
  @Override public TypeMem live_use( GVNGCM gvn, Node def ) { return _live; }
}
