package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;
import com.cliffc.aa.util.Ary;
import com.cliffc.aa.util.IBitSet;
import com.cliffc.aa.util.SB;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.BitSet;

// Split a set of aliases into a SESE region, to be joined by a later MemJoin.
// This allows more precision in the SESE that may otherwise merge many paths
// in and out, and is especially targeting non-inlined calls.
public class MemSplitNode extends Node {
  Ary<IBitSet> _escs = new Ary<>(new IBitSet[]{new IBitSet()});
  public MemSplitNode( Node mem ) { super(OP_SPLIT,null,mem); }
  Node mem() { return in(1); }
  public MemJoinNode join() {
    Node prj = ProjNode.proj(this,0);
    if( prj==null ) return null;
    return (MemJoinNode)prj._uses.at(0);
  }

  @Override boolean is_mem() { return true; }
  @Override String str() {
    SB sb = new SB();
    sb.p('(').p("base,");
    for( int i=1; i<_escs._len; i++ )
      _escs.at(i).toString(sb).p(',');
    return sb.unchar().p(')').toString();
  }
  @Override public Node ideal(GVNGCM gvn, int level) { return null; }
  @Override public Type value(GVNGCM gvn) {
    Type t = gvn.type(mem());
    if( !(t instanceof TypeMem) ) return t.oob();
    // Normal type is for an MProj of the input memory, one per alias class
    Type[] ts = TypeAry.get(_escs._len);
    Arrays.fill(ts,t);
    return TypeTuple.make(ts);
  }
  @Override public boolean basic_liveness() { return false; }

  // Find the escape set this esc set belongs to, or make a new one.
  int add_alias( GVNGCM gvn, IBitSet esc ) {
    IBitSet all = _escs.at(0);     // Summary of Right Hand Side(s) escapes
    if( all.disjoint(esc) ) {      // No overlap
      _escs.set(0,all.or(esc));    // Update summary
      _escs.add(esc);              // Add escape set
      gvn.setype(this,value(gvn)); // Expand tuple result
      return _escs._len-1;
    }
    for( int i=1; i<_escs._len; i++ )
      if( esc.subsetsX(_escs.at(i)) )
        return i;               // Found exact alias slice
    return 0;                   // No match, partial overlap
  }
  void remove_alias( GVNGCM gvn, int idx ) {
    // Remove (non-overlapping) bits from the rollup
    _escs.at(0).subtract(_escs.at(idx));
    _escs.remove(idx);          // Remove the escape set
    TypeTuple tt = (TypeTuple)value(gvn);
    gvn.setype(this,tt);        // Reduce tuple result
    // Renumber all trailing projections to match
    for( Node use : _uses ) {
      MProjNode mprj = (MProjNode)use;
      if( mprj._idx > idx ) {
        gvn.unreg(mprj);
        mprj._idx--;
        gvn.rereg(mprj,tt.at(mprj._idx));
      }
    }
  }

  // A function body was cloned and all aliases split.  The 'this' Split takes
  // the first child and the clone takes the 2nd child.
  void split_alias( Node copy, BitSet aliases, GVNGCM gvn ) {
    gvn.add_work(this);
    MemSplitNode cmsp = (MemSplitNode)copy;
    for( int alias = aliases.nextSetBit(0); alias != -1; alias = aliases.nextSetBit(alias + 1)) {
      int[] kid0_aliases = BitsAlias.get_kids(alias);
      int newalias1 = kid0_aliases[1];
      int newalias2 = kid0_aliases[2];
      cmsp._update(alias,newalias1);
      this._update(alias,newalias2);
      gvn.add_work(join());
    }
  }
  // Replace the old alias with the new child alias
  private void _update(int oldalias, int newalias) {
    IBitSet esc0 = _escs.at(0);
    if( esc0.tst(oldalias) ) {
      esc0.clr(oldalias);
      esc0.set(newalias);
      for( int i=1; i<_escs._len; i++ ) {
        IBitSet esc = _escs.at(i);
        if( esc.tst(oldalias) ) {
          esc.clr(oldalias);
          esc.set(newalias);
          break;
        }
      }
    }
  }

  //@SuppressWarnings("unchecked")
  @Override @NotNull public MemSplitNode copy( boolean copy_edges, GVNGCM gvn) {
    MemSplitNode nnn = (MemSplitNode)super.copy(copy_edges, gvn);
    nnn._escs = _escs.deepCopy();
    return nnn;
  }
  @Override public Node is_copy(GVNGCM gvn, int idx) {
    if( _uses._len==1 && _keep==0 ) return mem(); // Single user
    return null;
  }
    // Modifies all of memory - just does it in parts
  @Override IBitSet escapees(GVNGCM gvn) { return IBitSet.FULL; }
}
