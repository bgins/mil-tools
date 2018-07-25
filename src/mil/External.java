/*
    Copyright 2018 Mark P Jones, Portland State University

    This file is part of mil-tools.

    mil-tools is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    mil-tools is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with mil-tools.  If not, see <https://www.gnu.org/licenses/>.
*/
package mil;

import compiler.*;
import compiler.Failure;
import compiler.Handler;
import compiler.Position;
import core.*;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.HashMap;

public class External extends TopDefn {

  private String id;

  private Scheme declared;

  private String ref;

  private Type[] ts;

  /** Default constructor. */
  public External(Position pos, String id, Scheme declared, String ref, Type[] ts) {
    super(pos);
    this.id = id;
    this.declared = declared;
    this.ref = ref;
    this.ts = ts;
  }

  private static int count = 0;

  public External(Position pos, Scheme declared, String ref, Type[] ts) {
    this(pos, "e" + count++, declared, ref, ts);
  }

  /**
   * Return references to all components of this top level definition in an array of
   * atoms/arguments.
   */
  Atom[] tops() {
    return new TopExt[] {new TopExt(this)};
  }

  /** Get the declared type, or null if no type has been set. */
  public Scheme getDeclared() {
    return declared;
  }

  /** Set the declared type. */
  public void setDeclared(Scheme declared) {
    this.declared = declared;
  }

  /** Return the identifier that is associated with this definition. */
  public String getId() {
    return id;
  }

  public String toString() {
    return id;
  }

  /** Find the list of Defns that this Defn depends on. */
  public Defns dependencies() {
    return null;
  }

  boolean dotInclude() {
    return false;
  }

  void displayDefn(PrintWriter out) {
    out.print("external " + id);
    if (ref != null) {
      out.print(" {" + ref);
      for (int i = 0; i < ts.length; i++) {
        out.print(" ");
        out.print(ts[i].toString(TypeWriter.ALWAYS));
      }
      out.print("}");
    }
    out.println(" :: " + declared);
  }

  /** Return a type for an instantiated version of this item when used as Atom (input operand). */
  public Type instantiate() {
    return declared.instantiate();
  }

  /**
   * Set the initial type for this definition by instantiating the declared type, if present, or
   * using type variables to create a suitable skeleton. Also sets the types of bound variables.
   */
  void setInitialType() throws Failure {
    /* Nothing to do here */
  }

  /**
   * Type check the body of this definition, but reporting rather than throwing' an exception error
   * if the given handler is not null.
   */
  void checkBody(Handler handler) throws Failure {
    /* Nothing to do here */
  }

  /** Type check the body of this definition, throwing an exception if there is an error. */
  void checkBody(Position pos) throws Failure {
    /* Nothing to do here */
  }

  /**
   * Calculate a generalized type for this binding, adding universal quantifiers for any unbound
   * type variable in the inferred type. (There are no "fixed" type variables here because all mil
   * definitions are at the top level.)
   */
  void generalizeType(Handler handler) throws Failure {
    /* nothing to do here */
  }

  void findAmbigTVars(Handler handler, TVars gens) {
    /* Nothing to do here */
  }

  /** First pass code generation: produce code for top-level definitions. */
  void generateMain(Handler handler, MachineBuilder builder) {
    handler.report(new Failure(pos, "Cannot access external symbol \"" + id + "\" from bytecode"));
  }

  /** Apply inlining. */
  public void inlining() {
    /* Nothing to do here */
  }

  /**
   * Count the number of unused arguments for this definition using the current unusedArgs
   * information for any other items that it references.
   */
  int countUnusedArgs() {
    return 0;
  }

  /** Rewrite this program to remove unused arguments in block calls. */
  void removeUnusedArgs() {
    /* Nothing to do here */
  }

  public void flow() {
    /* Nothing to do here */
  }

  /**
   * Compute a summary for this definition (if it is a block or top-level) and then look for a
   * previously encountered item with the same code in the given table. Return true if a duplicate
   * was found.
   */
  boolean summarizeDefns(Blocks[] blocks, TopLevels[] topLevels) {
    return false;
  }

  void eliminateDuplicates() {
    /* Nothing to do here */
  }

  void collect() {
    /* Nothing to do here */
  }

  void collect(TypeSet set) {
    declared = declared.canonScheme(set);
    for (int i = 0; i < ts.length; i++) {
      ts[i] = ts[i].canonType(set);
    }
  }

  /** Apply constructor function simplifications to this program. */
  void cfunSimplify() {
    /* Nothing to do here */
  }

  void printlnSig(PrintWriter out) {
    out.println("external " + id + " :: " + declared);
  }

  /** Test to determine if this is an appropriate definition to match the given type. */
  External isExternalOfType(Scheme inst) {
    return declared.alphaEquiv(inst) ? this : null;
  }

  External(External e) {
    this(e.pos, e.declared, e.ref, e.ts);
  }

  /** Handle specialization of Externals */
  void specialize(MILSpec spec, External eorig) {
    debug.Log.println(
        "External specialize: "
            + eorig
            + " :: "
            + eorig.declared
            + "  ~~>  "
            + this
            + " :: "
            + this.declared);
  }

  /**
   * Generate a specialized version of an entry point. This requires a monomorphic definition (to
   * ensure that the required specialization is uniquely determined, and to allow the specialized
   * version to share the same name as the original.
   */
  Defn specializeEntry(MILSpec spec) throws Failure {
    throw new ExternalAsEntrypoint(this);
  }

  /** Update all declared types with canonical versions. */
  void canonDeclared(MILSpec spec) {
    declared = declared.canonScheme(spec);
  }

  void topLevelrepTransform(RepTypeSet set) {
    declared = declared.canonType(set);
    debug.Log.println("Determining representation for external " + id + " :: " + declared);
    Type[] r = declared.repCalc();
    Tail t = generateTail();
    if (t == null) { // Program will continue to use an external definition
      if (r != null) { // Check for a change in representation
        if (r.length != 1) {
          // TODO: do something to avoid the following error
          debug.Internal.error(
              "Cannot handle change of representation for external " + id + " :: " + declared);
        }
        impl = new External(pos, id, r[0], null, null); // do not copy ref or ts
        debug.Log.println("Replaced external definition with " + id + " :: " + r[0]);
      }
    } else { // Generator has produced an implementation for this external
      TopLhs[] lhs; // Create a left hand side for the new top level definition
      if (r == null) { // no change in type representation:
        lhs = new TopLhs[] {new TopLhs()};
        lhs[0].setDeclared(declared);
      } else {
        lhs = new TopLhs[r.length];
        for (int i = 0; i < r.length; i++) {
          lhs[i] = new TopLhs();
          lhs[i].setDeclared(r[i]);
        }
      }
      // TODO: it seems inconsistent to use a HashMap for topLevelRepMap, while using a field here
      // ...
      impl =
          new TopLevel(
              pos, lhs, t); // Make new top level to use as the replacement for this External
      debug.Log.println("Generated new top level definition for " + impl);
    }
  }

  private TopDefn impl = null;

  Atom[] repExt() {
    return (impl == null) ? null : impl.tops();
  }

  /**
   * Stores a mapping from String references to generators for external function implementations.
   */
  private static HashMap<String, ExternalGenerator> generators = new HashMap();

  /**
   * Use the ref and ts fields to determine if we can generate an implementation, post
   * representation transformation, for an external primitive.
   */
  Tail generateTail() {
    if (ref != null && ts != null) { // Do not generate code if ref or ts is missing
      ExternalGenerator gen = generators.get(ref);
      if (gen != null && ts.length >= gen.needs) {
        return gen.generate(pos, ref, ts);
      }
    }
    return null; // TODO: fix this!
  }

  static {

    // primBitFromLiteral v w ... :: Proxy -> Bit w
    generators.put(
        "primBitFromLiteral",
        new ExternalGenerator(2) {
          Tail generate(Position pos, String ref, Type[] ts) {
            BigInteger v = ts[0].getNat(); // Value of literal
            BigInteger w = ts[1].getNat(); // Width of bit vector
            if (v != null && w != null) {
              Tail t = new Return(IntConst.words(v, w.intValue()));
              ClosureDefn k = new ClosureDefn(pos, Temp.noTemps, Temp.noTemps, t);
              return new ClosAlloc(k).withArgs(Atom.noAtoms);
            }
            return null; // TODO: generate error message?  throw exception?
          }
        });

    // primBitNegate w :: Bit w -> Bit w
    generators.put(
        "primBitNegate",
        new ExternalGenerator(1) {
          Tail generate(Position pos, String ref, Type[] ts) {
            BigInteger w = ts[0].getNat(); // Width of bit vector
            if (w != null) {
              int width = w.intValue();
              int n = Type.numWords(width);
              Temp[] vs = Temp.makeTemps(n); // variables returned from block
              Temp[] ws = Temp.makeTemps(n); // arguments to closure
              Code code = new Done(new Return(Temp.clone(vs)));
              int rem = width % Type.WORDSIZE; // nonzero => unused bits in most sig word

              // Use Prim.xor on the most significant word if not all bits are used:
              if (rem != 0) {
                Temp v = vs[--n];
                code = new Bind(v, Prim.xor.withArgs(vs[n] = new Temp(), (1 << rem) - 1), code);
              }

              // Use Prim.neg on any remaining words:
              while (n > 0) {
                Temp v = vs[--n];
                code = new Bind(v, Prim.neg.withArgs(vs[n] = new Temp()), code);
              }

              // Package up code in a block:
              Block b = new Block(pos, vs, code);

              // Define a closure for the function:
              ClosureDefn k = new ClosureDefn(pos, Temp.noTemps, ws, new BlockCall(b).withArgs(ws));
              return new ClosAlloc(k).withArgs(Atom.noAtoms);
            }
            return null; // TODO: generate error message?  throw exception?
          }
        });

    // ...
  }

  /** Rewrite the components of this definition to account for changes in representation. */
  void repTransform(Handler handler, RepTypeSet set) {
    /* Processing of External definitions was completed during the first pass. */
  }

  /** Add this exported definition to the specified MIL environment. */
  void addExport(MILEnv exports) {
    exports.addTop(id, new TopExt(this));
  }

  /**
   * Find the arguments that are needed to enter this definition. The arguments for each block or
   * closure are computed the first time that we visit the definition, but are returned directly for
   * each subsequent call.
   */
  Temp[] addArgs() throws Failure {
    return null;
  }

  void countCalls() {
    /* Nothing to do here */
  }
}
