package com.cliffc.aa;

import com.cliffc.aa.node.*;
import com.cliffc.aa.type.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestLive {
  @Test public void testBasic() {
    GVNGCM gvn = new GVNGCM();

    // Liveness is a backwards flow.  Scope always demands all return results.
    ScopeNode scope = new ScopeNode(null,false);

    Node fullmem = new ConNode<>(TypeMem.FULL);
    gvn.setype(fullmem,TypeMem.FULL);
    scope.set_mem(fullmem,gvn);

    // Return the number '5' - should be alive with no special memory.
    Node rez = new ConNode<>(TypeInt.con(5));
    gvn.setype(rez,TypeInt.con(5));
    scope.set_rez(rez,gvn);

    // Check liveness base case
    scope._live = scope.live(gvn);
    assertEquals(scope._live,TypeMem.ANYMEM);

    // Check liveness recursive back one step
    rez._live = rez.live(gvn);
    assertEquals(rez._live,TypeMem.ALIVE);
  }

  @SuppressWarnings("unchecked")
  @Test public void testNewObj() {
    Env env = Env.top_scope();
    GVNGCM gvn = Env.GVN;
    GVNGCM._INIT0_CNT = 1; // No prims
    // Always memory for the NewObj
    Node mmm = new ConNode<>(TypeMem.ANYMEM);
    gvn.setype(mmm,TypeMem.ANYMEM);

    // Fields
    Type ti5 = TypeInt.con(5);
    ConNode fdx = new ConNode(ti5);
    gvn.setype(fdx,ti5);
    Type ti9 = TypeInt.con(9);
    ConNode<Type> fdy = new ConNode<>(ti9);
    gvn.setype(fdy,ti9);

    // New object, fields x,y holding ints
    NewObjNode nnn = new NewObjNode(false,TypeStruct.DISPLAY,mmm,gvn.con(Type.NIL));
    nnn.create_active("x",fdx,TypeStruct.FFNL,gvn);
    nnn.create_active("y",fdy,TypeStruct.FFNL,gvn);
    nnn.no_more_fields();
    gvn.setype(nnn,nnn.value(gvn));

    // Proj, OProj
    Node mem = new MProjNode(nnn,0);
    gvn.setype(mem,mem.value(gvn));
    Node ptr = new  ProjNode(nnn,1);
    gvn.setype(ptr,ptr.value(gvn));

    // Use the object for scope exit
    ScopeNode scope = new ScopeNode(null,false);
    scope.set_mem(mem,gvn);
    scope.set_rez(ptr,gvn);
    gvn.setype(scope,Type.ALL);

    // Check 'live' is stable on creation, except for mem & scope
    // which are 'turning around' liveness.
    // Value was computed in a forwards flow.
    for( Node n : new Node[]{mmm,fdx,fdy,nnn,mem,ptr,scope} ) {
      if( n != mem && n != scope )
        assertTrue(n.live(gvn).isa(n._live));
      assertEquals(gvn.type(n),n.value(gvn));
    }

    // Check liveness base case
    scope._live = scope.live(gvn);
    // Since simple forwards-flow, the default memory is known UNUSED.
    // However, we got provided at least one object.
    TypeMem expected_live = (TypeMem)gvn.type(mem);
    assertEquals(scope._live,expected_live);

    // Check liveness recursive back one step
    ptr._live = ptr.live(gvn);
    assertEquals(TypeMem.ALIVE,ptr._live); // Ptr is all_type, conservative so all memory alive
    mem._live = mem.live(gvn);
    assertEquals(mem._live,expected_live); // Object demands of OProj, but OProj passes along request to NewObj
    nnn._live = nnn.live(gvn);
    assertEquals(expected_live,nnn._live); // NewObj supplies object, needs what its input needs
    mmm._live = mmm.live(gvn);
    assertEquals(TypeMem.ALIVE,mmm._live); // Since ptr is scalar, all memory is alive
    fdx._live = fdx.live(gvn);
    assertEquals(TypeMem.ALIVE,fdx._live); // Since ptr is scalar, all memory is alive

  }
}
