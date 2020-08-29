package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;

// Proj memory
public class MrgProjNode extends ProjNode {
  public MrgProjNode( NewNode nnn, Node mem ) { super(0,nnn,mem); }
  @Override public String xstr() { return "MrgProj"+_idx; }
  @Override public boolean is_mem() { return true; }
  NewNode nnn() { return (NewNode)in(0); }
  Node    mem() { return          in(1); }
  @Override public Node ideal(GVNGCM gvn, int level) {
    boolean doe = false;        // Dead-On-Entry
    Type t = mem()._val;
    if( t instanceof TypeMem && ((TypeMem)t).at(nnn()._alias)==TypeObj.UNUSED )
      doe = true;                  // Dead-On-Entry

    if( doe && nnn().is_unused() ) // New is dead for no pointers
      return mem();

    // New is dead from below.
    if( _live.at(nnn()._alias)==TypeObj.UNUSED && nnn()._keep==0 && !nnn().is_unused() ) {
      gvn.unreg(nnn());         // Unregister before self-kill
      nnn().kill(gvn);          // Killing a NewNode has to do more updates than normal
      return this;
    }
    if( doe && nnn().is_unused() )
      return mem();             // Kill MrgNode when it no longer lifts values

    return null;
  }
  @Override public Type value(GVNGCM.Mode opt_mode) {
    if( !(in(0) instanceof NewNode) ) return Type.ANY;
    NewNode nnn = nnn();
    Type tn = nnn._val;
    Type tm = mem()._val;
    if( !(tn instanceof TypeTuple) ) return tn.oob();
    if( !(tm instanceof TypeMem  ) ) return tm.oob();
    TypeObj to = (TypeObj)((TypeTuple)tn).at(0);
    TypeMem tmem = (TypeMem)tm;
    return nnn.is_unused()      // This is a cycle-breaking lifting value
      ? tmem.set   (nnn._alias,TypeObj.UNUSED)
      : tmem.st_new(nnn._alias, to);
  }

  @Override BitsAlias escapees() { return in(0).escapees(); }
  @Override public boolean basic_liveness() { return false; }
  // Only called here if alive, and input is more-than-basic-alive
  @Override public TypeMem live_use(GVNGCM.Mode opt_mode, Node def ) { return def==in(0) ? TypeMem.ALIVE : _live; }
}
