package com.cliffc.aa.node;

import com.cliffc.aa.*;
import com.cliffc.aa.type.Type;
import com.cliffc.aa.type.TypeMem;
import com.cliffc.aa.type.TypeTuple;

// Proj data
public class ProjNode extends Node {
  public int _idx;
  public ProjNode( int idx, Node head ) { this(OP_PROJ,head,idx); }
  public ProjNode( int idx, Node... ns ) { super(OP_PROJ,ns); _idx=idx; }
  ProjNode( byte op, Node ifn, int idx ) { super(op,ifn); _idx=idx; }
  @Override
  public String xstr() { return "DProj"+_idx; }

  @Override public Node ideal(GVNGCM gvn, int level) {
    Node c = in(0).is_copy(gvn,_idx);
    if( c != null ) return c==this ? gvn.con(Type.ANY) : c; // Happens in dying loops
    return null;
  }

  @Override public Type value(GVNGCM.Mode opt_mode) {
    Type c = val(0);
    if( c instanceof TypeTuple ) {
      TypeTuple ct = (TypeTuple)c;
      if( _idx < ct._ts.length )
        return ct._ts[_idx];
    }
    return c.oob();
  }
  // Only called here if alive, and input is more-than-basic-alive
  @Override public TypeMem live_use(GVNGCM.Mode opt_mode, Node def ) { return def.basic_liveness() ? _live : TypeMem.ANYMEM; }

  public static ProjNode proj( Node head, int idx ) {
    for( Node use : head._uses )
      if( use instanceof ProjNode && ((ProjNode)use)._idx==idx )
        return (ProjNode)use;
    return null;
  }

  @Override public int hashCode() { return super.hashCode()+_idx; }
  @Override public boolean equals(Object o) {
    if( this==o ) return true;
    if( !super.equals(o) ) return false;
    if( !(o instanceof ProjNode) ) return false;
    ProjNode proj = (ProjNode)o;
    return _idx==proj._idx;
  }
  @Override public byte op_prec() { return in(0).op_prec(); }
}
