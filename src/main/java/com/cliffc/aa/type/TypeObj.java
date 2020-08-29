package com.cliffc.aa.type;

import com.cliffc.aa.util.VBitSet;
import java.util.function.Predicate;

// Types which extend memory-based objects - currently Structs (which include
// tuples but not TypeTuple) and Str (Strings); will include Arrays at some
// point.  Structs have memory words addressed by a base-pointer and a field
// name (for tuples, the field number), and Arrays have memory words addressed
// by a base-pointer and an index.
public class TypeObj<O extends TypeObj<O>> extends Type<O> {
  boolean _any;                 // True=choice/join; False=all/meet
  boolean _use;                 // Equal to _any for OBJ/XOBJ; unequal to _any for UNUSED/ISUSED
  protected TypeObj  (byte type, String name, boolean any) { this(type,name,any,any); }
  protected void init(byte type, String name, boolean any) { init(type,name,any,any); }
  private TypeObj    (byte type, String name, boolean any, boolean use) { super(type); init(type,name,any,use); }
  private void init  (byte type, String name, boolean any, boolean use) { super.init(type, name); _any=any; _use=use; }
  // Hash does not depend on other types, so never changes
  @Override int compute_hash() { return super.compute_hash()+(_any?1:0)+(_use?2:0); }
  @Override public boolean equals( Object o ) {
    if( this==o ) return true;
    if( !(o instanceof TypeObj) || !super.equals(o) ) return false;
    TypeObj to = (TypeObj)o;
    return _any ==to._any && _use ==to._use;
  }
  @Override public boolean cycle_equals( Type o ) { return equals(o); }
  @Override String str( VBitSet dups ) {
    String x = _any==_use ? "obj" : "use";
    return _name+((_any?"~":"")+x);
  }

  private static TypeObj FREE=null;
  @Override protected O free( O ret ) { FREE=this; return ret; }
  @SuppressWarnings("unchecked")
  private static TypeObj make( String name, boolean any, boolean use ) {
    TypeObj t1 = FREE;
    if( t1 == null ) t1 = new TypeObj(TOBJ,name,any,use);
    else {  FREE = null;      t1.init(TOBJ,name,any,use); }
    TypeObj t2 = (TypeObj)t1.hashcons();
    return t1==t2 ? t1 : t1.free(t2);
  }
  public static final TypeObj OBJ   = make("",false,false); // Any obj; allocated as *something*
  public static final TypeObj ISUSED= make("",false,true ); // Possibly allocated, the worst possible result
  public static final TypeObj UNUSED= (TypeObj)ISUSED.dual();    // Never allocated
  public static final TypeObj XOBJ  = (TypeObj)OBJ   .dual();    // alloc, but object type is unknown (either struct or array)
  static final TypeObj[] TYPES = new TypeObj[]{OBJ,ISUSED,UNUSED,XOBJ};

  @Override boolean is_display() { return false; }
  @SuppressWarnings("unchecked")
  @Override protected O xdual() { return (O)new TypeObj(TOBJ,_name,!_any,!_use); }
  @Override protected Type xmeet( Type t ) {
    if( !(t instanceof TypeObj) ) return ALL;
    assert this!=t; // already handled
    TypeObj to = (TypeObj)t;
    if( _any ) {                // 'this' is high
      if( _use && t == UNUSED ) return XOBJ;
      return to; // Above all 't'
    }
    // 'this' is low
    if( !_use && t == ISUSED ) return ISUSED;
    return this;
  }
      
  // Update (approximately) the current TypeObj.  Merges fields.
  public TypeObj update(byte fin, String fld, Type val) { return this; }
  // Approximate array update.
  public TypeObj update(TypeInt idx, Type val) {
    if( this==ISUSED || this==OBJ ) return this;
    return TypeAry.ARY.dual().update(idx,val);
  }
  // Exact object update.  Replaces fields.
  public TypeObj st    (byte fin, String fld, Type val) { return this; }
  // Keep the same basic type, and meet related fields.  Type error if basic
  // types are unrelated.
  public TypeObj st_meet(TypeObj obj) {
    if( _any || obj._any )  return (TypeObj)meet(obj);  // One is high, so just keep other side
    return obj.crush();    // Both are low, so keep other fields but crush them
  }
  TypeObj flatten_fields() { return this; }
  
  @Override public boolean above_center() { return _any; }
  @Override public boolean may_be_con() { return _any; }
  @Override public boolean is_con() { return false; }
  @Override public boolean must_nil() { return false; }
  @Override public boolean  may_nil() { return false; }
  @Override void walk( Predicate<Type> p ) { p.test(this); }
  // Widen (loss info), to make it suitable as the default function memory.
  // Must be monotonic, as CallEpiNode.value() uses this.
  public TypeObj crush() { return this; }
}
