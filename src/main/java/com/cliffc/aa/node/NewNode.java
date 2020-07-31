package com.cliffc.aa.node;

import com.cliffc.aa.Env;
import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;
import com.cliffc.aa.util.IBitSet;
import org.jetbrains.annotations.NotNull;

// Allocates a TypeObj and produces a Tuple with the TypeObj and a TypeMemPtr.
//
// NewNodes have a unique alias class - they do not alias with any other
// NewNode, even if they have the same type.  Upon cloning both NewNodes get
// new aliases that inherit (tree-like) from the original alias.

public abstract class NewNode<T extends TypeObj<T>> extends Node {
  // Unique alias class, one class per unique memory allocation site.
  // Only effectively-final, because the copy/clone sets a new alias value.
  public int _alias; // Alias class

  // Set of aliases read or written by this node.  Only _alias.
  public IBitSet _escapees;

  // A list of field names and field-mods, folded into the initial state of
  // this NewObj.  These can come from initializers at parse-time, or stores
  // folded in.  There are no types stored here; types come from the inputs.
  public T _ts;             // Base object type, representing all possible future values

  // The memory state for Env.DEFMEM, the default memory.  All non-final fields
  // are ALL; final fields keep their value.  All field flags are moved to
  // bottom, e.g. as-if all fields are now final-stored.  Will be set to
  // TypeObj.UNUSE for never-allocated (eg dead allocations)
  TypeObj _crushed;

  // Just TMP.make(_alias,OBJ)
  public TypeMemPtr _tptr;

  // NewNodes can participate in cycles, where the same structure is appended
  // to in a loop until the size grows without bound.  If we detect this we
  // need to approximate a new cyclic type.
  public final static int CUTOFF=2; // Depth of types before we start forcing approximations

  // NewNodes do not really need a ctrl; useful to bind the upward motion of
  // closures so variable stores can more easily fold into them.
  public NewNode( byte type, int parent_alias, T to, Node mem         ) { super(type,null,mem    ); _init(parent_alias,to); }
  public NewNode( byte type, int parent_alias, T to, Node mem,Node fld) { super(type,null,mem,fld); _init(parent_alias,to); }
  private void _init(int parent_alias, T to) {
    _alias = BitsAlias.new_alias(parent_alias);
    _tptr = TypeMemPtr.make(_alias,TypeObj.ISUSED);
    _escapees = new IBitSet();
    _escapees.set(_alias);
    sets(to);
  }
  public String xstr() { return "New"+"*"+_alias; } // Self short name
  public String  str() { return "New"+_ts; } // Inline less-short name

  @Override boolean is_mem() { return true; }
  Node mem() { return in(1); }
  static int def_idx(int fld) { return fld+2; } // Skip mem in slot 1
  Node fld(int fld) { return in(def_idx(fld)); } // Node for field#

  // Recompute default memory cache on a change
  @SuppressWarnings("unchecked")
  protected final void sets( T ts ) {
    _ts = ts;
    _crushed = ts.crush();
  }

  @SuppressWarnings("unchecked")
  @Override public Node ideal(GVNGCM gvn, int level) {
    Type tmem = gvn.type(mem());
    assert tmem instanceof TypeMem || tmem==Type.ANY || tmem==Type.ALL;
    // If either the address or memory is not looked at then the memory
    // contents are dead.  The object might remain as a 'gensym' or 'sentinel'
    // for identity tests.
    if( _defs._len > 2 && captured(gvn) ) {
      while( !is_dead() && _defs._len > 2 )
        pop(gvn);               // Kill all fields except memory
      sets(dead_type());
      gvn.set_def_reg(Env.DEFMEM,_alias,gvn.add_work(gvn.con(TypeObj.UNUSED)));
      if( is_dead() ) return this;
      gvn.add_work_uses(_uses.at(0));  // Get FPtrs from MProj from this
      return this;
    }

    return null;
  }

  @Override IBitSet escapees(GVNGCM gvn) { return _escapees; }
  abstract T dead_type();
  @Override public boolean basic_liveness() { return false; }
  @Override public TypeMem live_use( GVNGCM gvn, Node def ) { return _live; }

  // Basic escape analysis.  If no escapes and no loads this object is dead.
  private boolean captured( GVNGCM gvn ) {
    if( _keep > 0 ) return false;
    if( _uses._len==0 ) return false; // Dead or being created
    Node mem = _uses.at(0);
    // If only either address or memory remains, then memory contents are dead
    if( _uses._len==1 ) {
      if( mem instanceof MProjNode ) return true; // No pointer, just dead memory
      // Just a pointer; currently on Strings become memory constants and
      // constant-fold - leaving the allocation dead.
      return !(gvn.type(in(1)) instanceof TypeStr);
    }
    Node ptr = _uses.at(1);
    if( ptr instanceof MProjNode ) ptr = _uses.at(0); // Get ptr not mem

    // Scan for memory contents being unreachable.
    // Really stupid!
    for( Node use : ptr._uses )
      if( !(use instanceof IfNode) )
        return false;
    // Only used to nil-check (always not-nil) and equality (always unequal to
    // other aliases).
    return true;
  }

  boolean escaped(GVNGCM gvn) {
    if( gvn._opt_mode==0 ) return true; // Assume escaped in parser
    if( _uses._len!=2 ) return false; // Dying/dead, not escaped
    Node ptr = _uses.at(0);
    if( ptr instanceof MProjNode ) ptr = _uses.at(1); // Get ptr not mem
    return ptr._live==TypeMem.ESCAPE;
  }

  // clones during inlining all become unique new sites
  @SuppressWarnings("unchecked")
  @Override @NotNull public NewNode copy( boolean copy_edges, GVNGCM gvn) {
    // Split the original '_alias' class into 2 sub-aliases
    NewNode<T> nnn = (NewNode<T>)super.copy(copy_edges, gvn);
    nnn._init(_alias,_ts);      // Children alias classes, split from parent
    // The original NewNode also splits from the parent alias
    assert gvn.touched(this);
    Type oldt = gvn.unreg(this);
    _init(_alias,_ts);
    gvn.rereg(this,oldt);
    return nnn;
  }

  @Override public int hashCode() { return super.hashCode()+ _alias; }
  // Only ever equal to self, because of unique _alias.  We can collapse equal
  // NewNodes and join alias classes, but this is not the normal CSE and so is
  // not done by default.
  @Override public boolean equals(Object o) {  return this==o; }
  // If object is dead, memory is a pass-thru
  @Override public Node is_copy(GVNGCM gvn, int idx) {
    if( idx!=0 ) return null;
    if( _ts != dead_type() ) return null;
    Type t = gvn.type(mem());
    if( !(t instanceof TypeMem) ) return null;
    TypeMem tm = (TypeMem)t;
    if( tm.at(_alias) != TypeObj.UNUSED )
      return null;
    return mem();
  }
}
