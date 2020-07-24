package com.cliffc.aa.node;

import com.cliffc.aa.GVNGCM;
import com.cliffc.aa.type.*;
import com.cliffc.aa.util.IBitSet;

// See CallNode.  Slot 0 is the Call.  The remaining slots are Returns which
// are typed as standard function returns: {Ctrl,Mem,Val}.  These Returns
// represent call-graph edges that are known to be possible and thus their fidx
// appears in the Call's BitsFun type.
//
// Pre-opto it is possible for the all-functions type to appear at a Call, and
// in this case the CallEpi must assume all possible calls may happen, they are
// just not wired up yet.
//
// It is also possible that we have discovered and wired up a function, but
// that the input/output types are not yet monotonic, and we do not flow values
// across those edges until the types align.  This is controlled with a small
// bit-vector.

public final class CallEpiNode extends Node {
  boolean _is_copy;         // One-shot flag set when inlining an entire single-caller-single-called
  public CallEpiNode( Node... nodes ) { super(OP_CALLEPI,nodes);  assert nodes[1] instanceof DefMemNode; }
  String xstr() { return ((is_dead() || is_copy()) ? "x" : "C")+"allEpi"; } // Self short name
  public CallNode call() { return (CallNode)in(0); }
  @Override boolean is_mem() { return true; }
  int nwired() { return _defs._len-2; }
  static int wire_num(int x) { return x+2; }
  RetNode wired(int x) { return (RetNode)in(x+2); }

  @Override public Node ideal(GVNGCM gvn, int level) {
    // If inlined, no further xforms.  The using Projs will fold up.
    if( is_copy() ) return null;

    CallNode call = call();
    Type tc = gvn.type(call);
    if( !(tc instanceof TypeTuple) ) return null;
    TypeTuple tcall = (TypeTuple)tc;
    if( CallNode.tctl(tcall) != Type.CTRL ) return null; // Call not executable
    // Get calls resolved function.
    BitsFun fidxs = CallNode.ttfp(tcall).fidxs();

    // If the call's allowed functions excludes any wired, remove the extras
    if( !fidxs.test(BitsFun.ALL) ) { // Shortcut
      for( int i = 0; i < nwired(); i++ ) {
        RetNode ret = wired(i);
        if( !fidxs.test_recur(ret.fidx()) ) { // Wired call not in execution set?
          assert !BitsFun.is_parent(ret.fidx());
          // Remove the edge.  Pre-GCP all CG edges are virtual, and are lazily
          // and pessimistically filled in by ideal calls.  During the course
          // of lifting types some of the manifested CG edges are killed.
          // Post-GCP all CG edges are manifest, but types can keep lifting
          // and so CG edges can still be killed.
          unwire(gvn,call,ret);
          return this; // Return with progress
        }
      }
    }

    // See if we can wire any new fidxs directly between Call/Fun and Ret/CallEpi
    if( check_and_wire(gvn) )
      return this;              // Return with progress

    // The one allowed function is already wired?  Then directly inline.
    // Requires this calls 1 target, and the 1 target is only called by this.
    if( nwired()==1 && fidxs.abit() != -1 ) { // Wired to 1 target
      RetNode ret = wired(0);                 // One wired return
      FunNode fun = ret.fun();
      if( fun != null && fun._defs._len==2 && // Function is only called by 1 (and not the unknown caller)
          call.err(gvn,true)==null &&   // And args are ok
          // And memory types are aligned
          gvn.type(ret.mem()).isa(((TypeTuple)gvn.self_type(this)).at(1)) &&
          !fun.noinline() ) {           // And not turned off
        assert fun.in(1).in(0)==call;   // Just called by us
        // TODO: Bring back SESE opts
        fun.set_is_copy(gvn);
        return inline(gvn,level, call, tcall, ret.ctl(), ret.mem(), ret.val(), null/*do not unwire, because using the entire function body inplace*/);
      }
    }

    // Only inline wired single-target function with valid args.  CallNode wires.
    if( nwired()!=1 ) return null; // More than 1 wired, inline only via FunNode
    int fidx = fidxs.abit();       // Could be 1 or multi
    if( fidx == -1 ) return null;  // Multi choices, only 1 wired at the moment.
    if( fidxs.above_center() ) return null; // Can be unresolved yet
    if( BitsFun.is_parent(fidx) ) return null; // Parent, only 1 child wired

    if( call.err(gvn,true)!=null ) return null; // CallNode claims args in-error, do not inline

    // Call allows 1 function not yet inlined, sanity check it.
    int cnargs = call.nargs();
    FunNode fun = FunNode.find_fidx(fidx);
    assert !fun.is_forward_ref() && !fun.is_dead()
      && gvn.type(fun) == Type.CTRL
      && fun.nargs() == cnargs; // All checked by call.err
    RetNode ret = fun.ret();    // Return from function
    if( ret==null ) return null;

    // Single choice; check compatible args and no conversions needed.
    TypeStruct formals = fun._sig._formals;
    for( Node parm : fun._uses ) {
      if( parm instanceof ParmNode && parm.in(0)==fun ) {
        int idx = ((ParmNode)parm)._idx;
        if( idx == -1 ) continue; // RPC not an arg
        Node arg = idx==-2 ? call.mem() : call.arg(idx);
        Type actual = gvn.type(arg);
        // Display arg comes from function pointer
        if( idx==0 ) actual = (actual instanceof TypeFunPtr) ? ((TypeFunPtr)actual)._disp : Type.SCALAR;
        Type tparm = gvn.type(parm); // Pre-GCP this should be the default type
        if( !actual.isa(tparm) ||  // Not compatible
            (idx >= 0 && actual.isBitShape(formals.at(idx)) == 99) ) { // Requires user-specified conversion
          return null;
        }
      }
    }


    // Check for several trivial cases that can be fully inlined immediately.
    Node cctl = call.ctl();
    Node cmem = call.mem();
    Node rctl = ret.ctl();      // Control being returned
    Node rmem = ret.mem();      // Memory  being returned
    Node rrez = ret.val();      // Value   being returned
    // If the function does nothing with memory, then use the call memory directly.
    if( (rmem instanceof ParmNode && rmem.in(0) == fun) || gvn.type(rmem)==TypeMem.XMEM )
      rmem = cmem;
    // Check that function return memory and post-call memory are compatible
    TypeTuple tself = (TypeTuple)gvn.type(this);
    if( !gvn.type(rmem).isa( tself.at(1)) )
      return null;

    // Check for zero-op body (id function)
    if( rrez instanceof ParmNode && rrez.in(0) == fun && cmem == rmem )
      return inline(gvn,level,call,tcall,cctl,cmem,call.arg(((ParmNode)rrez)._idx), ret);
    // Check for constant body
    Type trez = gvn.type(rrez);
    if( trez.is_con() && rctl==fun && cmem == rmem)
      return inline(gvn,level,call,tcall,cctl,cmem,gvn.con(trez),ret);

    // Check for a 1-op body using only constants or parameters and no memory effects
    boolean can_inline=!(rrez instanceof ParmNode) && rmem==cmem;
    for( Node parm : rrez._defs )
      if( parm != null && parm != fun &&
          !(parm instanceof ParmNode && parm.in(0) == fun) &&
          !(parm instanceof ConNode) )
        can_inline=false;       // Not trivial
    if( fun.noinline() ) can_inline=false;
    if( can_inline ) {
      Node irez = rrez.copy(false,gvn);// Copy the entire function body
      ProjNode proj = ProjNode.proj(this,2);
      irez._live = proj==null ? TypeMem.ALIVE : proj._live;
      for( Node parm : rrez._defs )
        irez.add_def((parm instanceof ParmNode && parm.in(0) == fun) ? call.argm(((ParmNode)parm)._idx,gvn) : parm);
      if( irez instanceof PrimNode ) ((PrimNode)irez)._badargs = call._badargs;
      return inline(gvn,level,call,tcall,cctl,cmem,gvn.add_work(gvn.xform(irez)),ret); // New exciting replacement for inlined call
    }

    return null;
  }

  // Used during GCP and Ideal calls to see if wiring is possible.
  // Return true if a new edge is wired
  public boolean check_and_wire( GVNGCM gvn ) {
    if( gvn._opt_mode == 0 ) return false; // Graph not formed yet
    if( !(gvn.type(this) instanceof TypeTuple) ) return false; // Collapsing
    CallNode call = call();
    Type tcall = gvn.type(call);
    if( !(tcall instanceof TypeTuple) ) return false;
    BitsFun fidxs = CallNode.ttfp(tcall)._fidxs;
    if( fidxs.above_center() )  return false; // Still choices to be made during GCP.

    // Check all fidxs for being wirable
    boolean progress = false;
    for( int fidx : fidxs ) {                 // For all fidxs
      if( BitsFun.is_parent(fidx) ) continue; // Do not wire parents, as they will eventually settle out
      FunNode fun = FunNode.find_fidx(fidx);  // Lookup, even if not wired
      if( fun.is_dead() ) continue;           // Already dead, stale fidx
      if( fun.is_forward_ref() ) continue;    // Not forward refs, which at GCP just means a syntax error
      RetNode ret = fun.ret();
      if( ret==null ) continue;               // Mid-death
      if( _defs.find(ret) != -1 ) continue;   // Wired already
      progress=true;
      wire1(gvn,call,fun,ret); // Wire Call->Fun, Ret->CallEpi
    }
    return progress;
  }

  // Wire the call args to a known function, letting the function have precise
  // knowledge of its callers and arguments.  This adds a edges in the graph
  // but NOT in the CG, until _cg_wired gets set.
  void wire1( GVNGCM gvn, CallNode call, FunNode fun, RetNode ret ) {
    assert _defs.find(ret)==-1; // No double wiring
    wire0(gvn,call,fun);
    // Wire self to the return
    gvn.add_work(this);
    gvn.add_def(this,ret);
    gvn.add_work(ret);
  }

  // Wire without the redundancy check, or adding to the CallEpi
  void wire0(GVNGCM gvn, CallNode call, FunNode fun) {
    // Wire.  During GCP, cannot call "xform" since e.g. types are not final
    // nor constant yet - and need to add all new nodes to the GCP worklist.
    // During iter(), must call xform() to register all new nodes.

    // Add an input path to all incoming arg ParmNodes from the Call.  Cannot
    // assert finding all args, because dead args may already be removed - and
    // so there's no Parm/Phi to attach the incoming arg to.
    for( Node arg : fun._uses ) {
      if( arg.in(0) != fun || !(arg instanceof ParmNode) ) continue;
      // See CallNode output tuple type for what these horrible magic numbers are.
      Node actual;
      int idx = ((ParmNode)arg)._idx;
      TypeMem live;
      switch( idx ) {
      case  0: actual = new FP2ClosureNode(call); live = TypeMem.ALIVE; break; // Filter Function Pointer to Closure
      case -1: actual = new ConNode<>(TypeRPC.make(call._rpc)); live = TypeMem.ALIVE; break; // Always RPC is a constant
      case -2: actual = new MProjNode(call,CallNode.MEMIDX); live = arg._live; break;    // Memory into the callee
      default: actual = idx >= call.nargs()              // Check for args present
          ? new ConNode<>(Type.XSCALAR) // Missing args, still wire (to keep FunNode neighbors) but will error out later.
          : new ProjNode(call,idx+CallNode.ARGIDX); // Normal args
        live = TypeMem.ESCAPE;
        break;
      }
      actual = gvn._opt_mode == 2 ? gvn.new_gcp(actual) : gvn.xform(actual);
      gvn.add_def(arg,actual);
      actual._live = (TypeMem)actual._live.meet(live);
      gvn.add_work(actual);
    }

    // Add matching control to function via a CallGraph edge.
    Node callgrf = new CProjNode(call,0);
    callgrf = gvn._opt_mode == 2 ? gvn.new_gcp(callgrf) : gvn.xform(callgrf);
    gvn.add_def(fun,callgrf);
  }

  // Merge call-graph edges, but the call-graph is not discovered prior to GCP.
  // If the Call's type includes all-functions, then the CallEpi must assume
  // unwired Returns may yet appear, and be conservative.  Otherwise it can
  // just meet the set of known functions.
  @Override public Type value(GVNGCM gvn) {
    if( _is_copy )           // Just return the arg types.
      return TypeTuple.make(Type.CTRL,gvn.type(in(1)),gvn.type(in(2)));
    Type tin0 = gvn.type(in(0));
    if( !(tin0 instanceof TypeTuple) )
      return tin0.oob();     // Weird stuff?
    TypeTuple tcall = (TypeTuple)tin0;
    if( tcall._ts.length <= CallNode.ARGIDX ) return tcall.oob(); // Weird stuff

    // Get Call result.  If the Call args are in-error, then the Call is called
    // and we flow types to the post-call.... BUT the bad args are NOT passed
    // along to the function being called.
    // tcall[0] = Control
    // tcall[1] = Memory passed around the functions.
    // tcall[2] = TypeFunPtr passed to FP2Closure
    // tcall[3+]= Arg types
    Type ctl = CallNode.tctl(tcall); // Call is reached or not?
    if( ctl != Type.CTRL && ctl != Type.ALL )
      return TypeTuple.CALLE.dual();
    TypeMem    tcmem  = CallNode.tmem(tcall); // Peel apart Call tuple
    TypeFunPtr tfptr  = CallNode.ttfpx(tcall);// Peel apart Call tuple

    if( tfptr==null || tfptr.is_forward_ref() ) return TypeTuple.CALLE; // Still in the parser.
    // NO fidxs, means we're not calling anything.
    BitsFun fidxs = tfptr.fidxs();
    if( fidxs==BitsFun.EMPTY ) return TypeTuple.CALLE.dual();
    if( fidxs.above_center() ) return TypeTuple.CALLE.dual(); // Not resolved yet

    // Default memory: global worse-case scenario
    Node defnode = in(1);
    Type defmem = gvn.type(defnode);
    
    // Any not-wired unknown call targets?
    if( fidxs!=BitsFun.FULL ) {
      // If fidxs includes a parent fidx, then it was split - currently exactly
      // in two.  If both children are wired, then proceed to merge both
      // children as expected; same if the parent is still wired (perhaps with
      // some children).  If only 1 child is wired, then we have an extra fidx
      // for a not-wired child.  If this fidx is really some unknown caller we
      // would have to get super conservative; but its actually just a recently
      // split child fidx.  If we get the RetNode via FunNode.find_fidx we suffer
      // the non-local progress curse.  If we get super conservative, we end up
      // rolling backwards (original fidx returned int; each split will only
      // ever return int-or-better).  So instead we "freeze in place".
      outerloop:
      for( int fidx : fidxs ) {
        int kids=0;
        for( int i=0; i<nwired(); i++ ) {
          int rfidx = wired(i)._fidx;
          if( fidx == rfidx ) continue outerloop; // Directly wired is always OK
          if( fidx == BitsFun.parent(rfidx) ) kids++; // Count wired kids of a parent fidx
        }
        if( BitsFun.is_parent(fidx) ) { // Child of a split parent, need both kids wired
          if( kids==2 ) continue;       // Both kids wired, this is ok
          return gvn.self_type(this);   // "Freeze in place"
        }
        if( gvn._opt_mode < 2 )  // Before GCP?  Fidx is an unwired unknown call target
          { fidxs = BitsFun.FULL; break; }
        assert gvn._opt_mode==2; // During GCP, still wiring, post GCP all are wired
      }
    }

    // Compute call-return value from all callee returns
    Type trez = Type.ANY;
    Type tmem = TypeMem.ANYMEM;
    if( fidxs == BitsFun.FULL ) { // Called something unknown
      trez = Type.ALL;            // Unknown target does worst thing
      tmem = defmem;
    } else {                    // All targets are known & wired
      for( int i=0; i<nwired(); i++ ) {
        RetNode ret = wired(i);
        if( fidxs.test_recur(ret._fidx) ) { // Can be wired, but later fidx is removed
          TypeTuple tret = (TypeTuple)gvn.type(ret);
          tmem = tmem.meet(tret.at(1));
          trez = trez.meet(tret.at(2));
        }
      }
    }

    // Compute return memory, generally as the meet of all wired returns.
    // However, can keep non-escaping aliases to their pre-call value;
    // can "discover" dead from DefMem ahead of wired returns.
    TypeMem tmem2 = (TypeMem)tmem;
    TypeMem tdefmem = (TypeMem)defmem;
    TypeObj[] tos = new TypeObj[defnode._defs._len];
    tos[1] = tmem2.at(1);
    for( int alias=2; alias<defnode._defs._len; alias++ ) {
      Node mprj = defnode.in(alias);
      // Expect Con:~use, Con:low or NewNode.
      // ConNode:~use is dead not escaping, or NewNode flagged _not_escaped
      TypeObj to;
      if( mprj==null )          // Nothing at DefMem
        to = null;              // Alias is unused
      else if( mprj instanceof ConNode && ((ConNode)mprj)._t.above_center() )
        to = ((ConNode)mprj)._t.oob(TypeObj.ISUSED); // Dead, take it
      else if( mprj instanceof MProjNode && ((NewNode)mprj.in(0))._not_escaped )
        to = (TypeObj)tcmem.at(alias).join(((NewNode)mprj.in(0))._crushed); // Not escaped, use pre-call joined with the crush
      else {
        to = tmem2.at(alias);   // Use post-call
        if( gvn._opt_mode < 2 ) // Use post-call, but at least as a good as the default
          to = (TypeObj)to.join(tdefmem.at(alias));
      }
      tos[alias] = to;
    }
    TypeMem tmem3 = TypeMem.make0(tos);

    return TypeTuple.make(Type.CTRL,tmem3,trez);
  }

  // Sanity check
  boolean sane_wiring() {
    CallNode call = call();
    ProjNode cprj = ProjNode.proj(call,0); // Control proj
    if( cprj==null ) return true; // Abort check, call is dead
    for( int i=0; i<nwired(); i++ ) {
      RetNode ret = wired(i);
      if( ret.is_copy() ) return true; // Abort check, will be misaligned until dead path removed
      FunNode fun = ret.fun();
      if( cprj._uses.find(fun) == -1 )
        return false;           // ith usage is ith wire
    }
    return true;
  }

  // Inline the CallNode.  Remove all edges except the results.  This triggers
  // "is_copy()", which in turn will trigger the following ProjNodes to inline.
  private Node inline( GVNGCM gvn, int level, CallNode call, TypeTuple tcall, Node ctl, Node mem, Node rez, RetNode ret ) {
    assert (level&1)==0; // Do not actually inline, if just checking that all forward progress was found
    assert nwired()==0 || nwired()==1; // not wired to several choices

    // Unwire any wired called function
    if( ret != null && nwired() == 1 && !ret.is_copy() ) // Wired, and called function not already collapsing
      unwire(gvn,call,ret);
    // Call is also is_copy and will need to collapse
    _is_copy = call._is_copy = true; // Call is also is-copy
    gvn.add_work_uses(call.keep());
    for( int i=0; i<call.nargs(); i++ ) // All args no longer escape
      gvn.add_work(call.arg(i));
    // Remove all edges except the inlined results
    ctl.keep();  mem.keep();  rez.keep();
    while( _defs._len > 0 ) pop(gvn);
    // Put results back on, and trigger is_copy to collapse
    add_def(ctl .unhook());
    add_def(mem .unhook());
    add_def(rez .unhook());
    add_def(call.unhook());     // Hook call, to get FIDX for value filtering.
    return this;
  }

  void unwire(GVNGCM gvn, CallNode call, RetNode ret) {
    assert sane_wiring();
    if( !ret.is_copy() ) {
      FunNode fun = ret.fun();
      for( int i = 1; i < fun._defs._len; i++ ) // Unwire
        if( fun.in(i).in(0) == call ) {
          gvn.set_def_reg(fun, i, gvn.con(Type.XCTRL));
          break;
        }
      gvn.add_work_uses(fun);
    }
    del(_defs.find(ret));
    assert sane_wiring();
  }

  @Override public boolean basic_liveness() { return false; }
  // Compute local contribution of use liveness to this def.  If the call is
  // Unresolved, then none of CallEpi targets are (yet) alive.
  @Override public TypeMem live_use( GVNGCM gvn, Node def ) {
    assert _keep==0;
    if( is_copy() ) return live_use_copy(def); // Target is as alive as our projs are.
    // Not a copy
    if( def==in(0) ) return _live; // The Call
    if( def==in(1) ) return _live; // The DefMem
    // Wired return.
    // The given function is alive, only if the Call will Call it.
    Type tcall = gvn.type(call());
    if( !(tcall instanceof TypeTuple) ) return tcall.above_center() ? TypeMem.DEAD : _live;
    BitsFun fidxs = CallNode.ttfp(tcall).fidxs();
    int fidx = ((RetNode)def).fidx();
    if( fidxs.above_center() || !fidxs.test(fidx) )
      return TypeMem.DEAD;    // Call does not call this, so not alive.
    return _live;
  }

  // If slot 0 is not a CallNode, we have been inlined.
  boolean is_copy() { return _is_copy; }
  boolean is_copy_aligned(GVNGCM gvn) {
    if( !_is_copy ) return false;
    // Flagged as an inline copy, to be removed.  To preserve monotonicity,
    // cannot remove until memory types align.  The called function can be
    // "behind" the Call/CallEpi pair and needs to catch up before directly
    // inlining.
    Type tself = gvn.self_type(this);
    Type tmem0 = tself instanceof TypeTuple ? ((TypeTuple)tself).at(1) : tself;
    Type tmem1 = gvn.type(in(1));
    return tmem1.isa(tmem0);    // Incoming inline memory isa self memory
  }
  @Override public Node is_copy(GVNGCM gvn, int idx) {
    return is_copy_aligned(gvn) ? in(idx) : null;
  }
  @Override Node is_pure_call() { return in(0) instanceof CallNode ? call().is_pure_call() : null; }
  // Return the set of updatable memory - including everything reachable from
  // every call argument (including the display), and any calls reachable from
  // there, transitively through memory.
  //
  // In practice, just the no-escape aliases
  @Override IBitSet escapees(GVNGCM gvn) { return IBitSet.FULL; }
}
