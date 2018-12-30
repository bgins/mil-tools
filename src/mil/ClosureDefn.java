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
import core.*;
import java.io.PrintWriter;

public class ClosureDefn extends Defn {

  protected String id;

  protected Temp[] params;

  protected Temp[] args;

  protected Tail tail;

  /** Default constructor. */
  public ClosureDefn(Position pos, String id, Temp[] params, Temp[] args, Tail tail) {
    super(pos);
    this.id = id;
    this.params = params;
    this.args = args;
    this.tail = tail;
  }

  private static int count = 0;

  public ClosureDefn(Position pos, Temp[] params, Temp[] args, Tail tail) {
    this(pos, "k" + count++, params, args, tail);
  }

  protected AllocType declared;

  protected AllocType defining;

  /** Get the declared type, or null if no type has been set. */
  public AllocType getDeclared() {
    return declared;
  }

  /** Set the declared type. */
  public void setDeclared(AllocType declared) {
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
    return tail.dependencies(null);
  }

  String dotAttrs() {
    return "style=filled, fillcolor=salmon";
  }

  /** Display a printable representation of this definition on the specified PrintWriter. */
  /** Display a printable representation of this definition on the specified PrintWriter. */
  void dump(PrintWriter out, boolean isEntrypoint) {
    if (declared != null) {
      if (isEntrypoint) {
        out.print("entrypoint ");
      }
      out.println(id + " :: " + declared);
    }

    Temps ts = renameTemps ? Temps.push(args, Temps.push(params, null)) : null;
    Call.dump(out, id, "{", params, "} ", ts);
    Atom.displayTuple(out, args, ts);
    out.print(" = ");
    tail.displayln(out, ts);
  }

  AllocType instantiate() {
    return (declared != null) ? declared.instantiate() : defining;
  }

  /**
   * Set the initial type for this definition by instantiating the declared type, if present, or
   * using type variables to create a suitable skeleton. Also sets the types of bound variables.
   */
  void setInitialType() throws Failure {
    // Pick new names for the parameters:
    Temp[] oldps = params;
    params = Temp.makeTemps(oldps.length);

    // Pick new names for the arguments:
    Temp[] oldas = args;
    args = Temp.makeTemps(oldas.length);

    // Update the tail with new temporary names:
    tail = tail.apply(TempSubst.extend(oldps, params, TempSubst.extend(oldas, args, null)));

    // Set initial types for temporaries:
    Type[] stored = Type.freshTypes(params);
    Type dom = Type.tuple(Type.freshTypes(args));
    rng = new TVar(Tyvar.tuple);
    Type result = Type.milfun(dom, rng);
    if (declared == null) {
      defining = new AllocType(stored, result);
    } else {
      defining = declared.instantiate();
      defining.storedUnifiesWith(pos, stored);
      defining.resultUnifiesWith(pos, result);
    }
  }

  /**
   * Record the expected type of value that is generated by the tail associated with this closure
   * definition.
   */
  private Type rng;

  /**
   * Type check the body of this definition, but reporting rather than throwing an exception error
   * if the given handler is not null.
   */
  void checkBody(Handler handler) throws Failure {
    try {
      checkBody(pos);
    } catch (Failure f) {
      // We can recover from a type error in this definition (at least for long enough to type
      // check other definitions) if the types are all declared (and there is a handler).
      if (allTypesDeclared() && handler != null) {
        handler.report(f); // Of course, we still need to report the failure
        defining = null; // Mark this definition as having failed to check
      } else {
        throw f;
      }
    }
  }

  /** Type check the body of this definition, throwing an exception if there is an error. */
  void checkBody(Position pos) throws Failure {
    tail.inferType(pos).unify(pos, rng);
  }

  /** Check that there are declared types for all of the items defined here. */
  boolean allTypesDeclared() {
    return declared != null;
  }

  /** Lists the generic type variables for this definition. */
  protected TVar[] generics = TVar.noTVars;

  void generalizeType(Handler handler) throws Failure {
    if (defining != null) { // defining will be null if previous stage of type checker failed
      TVars gens = defining.tvars();
      generics = TVar.generics(gens, null);
      AllocType inferred = defining.generalize(generics);
      debug.Log.println("Inferred " + id + " :: " + inferred);
      if (declared != null && !declared.alphaEquiv(inferred)) {
        throw new Failure(
            pos,
            "Declared type \""
                + declared
                + "\" for \""
                + id
                + "\" is more general than inferred type \""
                + inferred
                + "\"");
      } else {
        declared = inferred;
      }
      findAmbigTVars(handler, gens); // search for ambiguous type variables ...
    }
  }

  void findAmbigTVars(Handler handler, TVars gens) {
    String extras = TVars.listAmbigTVars(tail.tvars(gens), gens);
    if (extras != null) {
      // TODO: do we need to apply a skeleton() method to defining?
      handler.report(
          new Failure(
              pos,
              "Closure definition \""
                  + id
                  + "\" used at type "
                  + defining
                  + " with ambiguous type variables "
                  + extras));
    }
  }

  /** First pass code generation: produce code for top-level definitions. */
  void generateMain(Handler handler, MachineBuilder builder) {
    /* skip these definitions on first pass */
  }

  /** Second pass code generation: produce code for block and closure definitions. */
  void generateFunctions(MachineBuilder builder) {
    builder.resetFrame();
    builder.setAddr(this, builder.getNextAddr());
    builder.extend(args, 0);
    int o = args.length;
    builder.extend(params, o);
    for (int i = 0; i < params.length; i++) {
      builder.sel(i, o++);
    }
    tail.generateTailCode(builder, o);
  }

  /** Stores the list of closure definitions that have been derived from this definition. */
  private ClosureDefns derived = null;

  public ClosureDefn deriveWithKnownCons(Call[] calls) {
    // Look to see if we have already derived a suitable version of this ClosureDefn:
    for (ClosureDefns cs = derived; cs != null; cs = cs.next) {
      if (cs.head.hasKnownCons(calls)) {
        // Return pointer to previous occurrence, or decline the request to specialize
        // if the original closure definition already has the requested allocator pattern.
        return (this == cs.head) ? null : cs.head;
      }
    }

    // Given this closure definition, this{params} [args] = t, we want to be able to replace a
    // closure allocation
    // for this and a set of known constructors specified by calls[] with corresponding allocations
    // for a
    // specialized closure constructor, k, that is defined by:
    //    k{newparams} [newargs] = b[newparams++newargs]
    //    b[newparams++newargs]  = ... initializers for calls ...
    //                             newtail

    // newargs provides fresh names for args to avoid naming conflicts:
    Temp[] newargs = Temp.makeTemps(args.length);

    // make the new closure definition; the params and tail will be filled in later:
    ClosureDefn k =
        new ClosureDefnWithKnownCons(/*pos*/ null, /*params*/ null, newargs, null, calls);
    derived = new ClosureDefns(k, derived);

    // We pick temporary variables for new parameters:
    Temp[][] tss = Call.makeTempsFor(calls);

    // Combine old parameters and new temporaries to calculate newparams:
    if (tss == null) {
      k.params = params; // TODO: safe to reuse params, or should we make a copy?
      k.derived = new ClosureDefns(k, k.derived);
    } else {
      k.params = mergeParams(tss, params);
    }

    // Concatenate k.params and newargs to find parameters for b:
    Temp[] bparams = Temp.append(k.params, newargs);

    // Generate the code for the body of b using a suitably renamed version of tail:
    Tail newtail = tail.apply(TempSubst.extend(args, newargs, null));
    Code bcode = addInitializers(calls, params, tss, new Done(newtail));

    // Make the definition for the new block b:
    Block b = new Block(BuiltinPosition.pos, bparams, bcode); // TODO: diff position?

    // Fill in the tail for k:
    k.tail = new BlockCall(b, bparams);

    return k;
  }

  boolean hasKnownCons(Call[] calls) {
    return false;
  }

  /** Apply inlining. */
  public void inlining() {
    tail = tail.inlineTail();
  }

  void liftAllocators() {
    tail = tail.liftStaticAllocator();
  }

  /**
   * A bitmap that identifies the used arguments of this definition. The base case, with no used
   * arguments, can be represented by a null array. Otherwise, it will be a non null array, the same
   * length as the list of parameters, with true in positions corresponding to arguments that are
   * known to be used and false in all other positions.
   */
  private boolean[] usedArgs = null;

  /**
   * Counts the total number of used arguments in this definition; this should match the number of
   * true values in the usedArgs array.
   */
  private int numUsedArgs = 0;

  /** Reset the bitmap and count for the used arguments of this definition, where relevant. */
  void clearUsedArgsInfo() {
    usedArgs = null;
    numUsedArgs = 0;
  }

  /**
   * Count the number of unused arguments for this definition using the current unusedArgs
   * information for any other items that it references.
   */
  int countUnusedArgs() {
    return countUnusedArgs(params);
  }

  /**
   * Count the number of unused arguments for this definition. A zero count indicates that all
   * arguments are used.
   */
  int countUnusedArgs(Temp[] dst) {
    if (isEntrypoint) { // treat all entrypoint arguments as used
      // We don't have to set numUsedArgs and usedArgs because all uses are guarded by isEntrypoint
      // tests
      return 0;
    } else {
      int unused = dst.length - numUsedArgs; // count # of unused args
      if (unused > 0) { // skip if no unused args
        usedVars = usedVars(); // find vars used in body
        for (int i = 0; i < dst.length; i++) { // scan argument list
          if (usedArgs == null || !usedArgs[i]) { // skip if already known to be used
            if (dst[i].isIn(usedVars) && !duplicated(i, dst)) {
              if (usedArgs == null) { // initialize usedArgs for first use
                usedArgs = new boolean[dst.length];
              }
              usedArgs[i] = true; // mark this argument as used
              numUsedArgs++; // update counts
              unused--;
            }
          }
        }
      }
      return unused;
    }
  }

  private Temps usedVars;

  /**
   * A utility function that returns true if the variable at position i in the given array also
   * appears in some earlier position in the array. (If this condition applies, then we can mark the
   * later occurrence as unused; there is no need to pass the same variable twice.)
   */
  private static boolean duplicated(int i, Temp[] dst) {
    // Did this variable appear in an earlier position?
    for (int j = 0; j < i; j++) {
      if (dst[j] == dst[i]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the list of variables that are used in this definition. Variables that are mentioned in
   * BlockCalls or ClosAllocs are only included if the corresponding flag in usedArgs is set.
   */
  Temps usedVars() {
    return tail.usedVars(null);
  }

  /**
   * Find the list of variables that are used in a call to this definition, taking account of the
   * usedArgs setting so that we only include variables appearing in argument positions that are
   * known to be used.
   */
  Temps usedVars(Atom[] args, Temps vs) {
    if (isEntrypoint) { // treat all entrypoint arguments as used
      return useAllArgs(args, vs);
    } else if (usedArgs != null) { // ignore this call if no args are used
      for (int i = 0; i < args.length; i++) {
        if (usedArgs[i]) { // ignore this argument if the flag is not set
          vs = args[i].add(vs);
        }
      }
    }
    return vs;
  }

  /**
   * Use information about which and how many argument positions are used to trim down an array of
   * destinations (specifically, the formal parameters of a Block or a ClosureDefn).
   */
  Temp[] removeUnusedTemps(Temp[] dsts) {
    if (!isEntrypoint && numUsedArgs < dsts.length) { // Found some new, unused args
      Temp[] newTemps = new Temp[numUsedArgs];
      int j = 0;
      for (int i = 0; i < dsts.length; i++) {
        if (usedArgs != null && usedArgs[i]) {
          newTemps[j++] = dsts[i];
        } else {
          MILProgram.report("removing unused argument " + dsts[i] + " from " + this);
        }
      }
      return newTemps;
    }
    return dsts; // No newly discovered unused arguments
  }

  private ClosAllocs uses = null;

  public void calledFrom(ClosAlloc ca) {
    uses = new ClosAllocs(ca, uses);
  }

  /** Remove unused arguments from block calls and closure definitions. */
  void removeUnusedArgs() {
    if (!isEntrypoint && numUsedArgs < params.length) {
      MILProgram.report(
          "Rewrote closure definition "
              + this
              + " to eliminate "
              + (params.length - numUsedArgs)
              + " unused fields");
      params = removeUnusedTemps(params); // remove unused stored parameters
      if (declared != null) {
        declared = declared.removeStored(numUsedArgs, usedArgs);
      }
      for (ClosAllocs cas = uses; cas != null; cas = cas.next) {
        cas.head.removeUnusedArgs(numUsedArgs, usedArgs);
      }
    }
  }

  /** Perform flow analysis on this definition. */
  public void flow() {
    tail = tail.rewriteTail(this, null /* facts */);
    tail.liveness(null /*facts*/);
  }

  /**
   * Compute a Tail that gives the result of entering this closure given the arguments that are
   * stored in the closure (sargs) and the extra function arguments (fargs) that prompted us to
   * enter this closure in the first place.
   */
  Tail withArgs(Atom[] sargs, Atom[] fargs) {
    return tail.apply(TempSubst.extend(args, fargs, TempSubst.extend(params, sargs, null)));
  }

  private Src[] sources;

  void initSources() {
    sources = new Src[params.length];
    for (int i = 0; i < sources.length; i++) {
      sources[i] = new Join(this, i, null);
    }
  }

  void dumpSources(PrintWriter out) {
    dumpSources(out, toString(), "{", "}", sources);
  }

  /**
   * Traverse the abstract syntax tree to calculate initial values for the sources of the parameters
   * of each Block and Closure definition.
   */
  void calcSources() {
    tail.calcSources(this, params, Temp.extersect(params, args, null));
  }

  void updateSources(int i, Defn d, int j) {
    sources[i].updateSources(this, i, d, j);
  }

  void updateSources(int i, Defn d, int j, Join js) {
    int k = Join.find(d, js); // Does js already include a component d.k?
    if (k < 0) { // If not, add a new component for d.j
      sources[i] = new Join(d, j, js);
    } else if (j != k) { // Otherwise, if the existing component comes from a different
      sources[i] = Src.any; // argument, then b.i could be "any" value.
    }
  }

  void setSource(int i, Src src) {
    sources[i] = src;
  }

  boolean propagateSources() {
    boolean changed = false;
    for (int i = 0; i < sources.length; i++) {
      Src src = sources[i];
      Src nsrc = src.propagate();
      if (nsrc != src) {
        changed = true;
        sources[i] = nsrc;
      }
    }
    return changed;
  }

  Src propagate(int i, Src src) {
    return sources[i].join(src);
  }

  /**
   * Use results of invariant analysis to determine whether the specified parameter of this
   * definition is invariant in its defining SCC.
   */
  boolean isInvariant(int i) {
    return sources != null && sources[i].isInvariant();
  }

  /**
   * Compute an integer summary for a fragment of MIL code with the key property that alpha
   * equivalent program fragments have the same summary value.
   */
  int summary() {
    return id.hashCode();
  }

  /** Test to see if two ClosureDefn values are alpha equivalent. */
  boolean alphaClosureDefn(ClosureDefn that) {
    // Check for same number of parameters:
    if (this.params.length != that.params.length || this.args.length != that.args.length) {
      return false;
    }

    // Build lists of parameters:
    Temps thisvars = null;
    Temps thatvars = null;
    for (int i = 0; i < this.params.length; i++) {
      thisvars = this.params[i].add(thisvars);
      thatvars = that.params[i].add(thatvars);
    }
    for (int i = 0; i < this.args.length; i++) {
      thisvars = this.args[i].add(thisvars);
      thatvars = that.args[i].add(thatvars);
    }

    // Check bodies for alpha equivalence:
    return this.tail.alphaTail(thisvars, that.tail, thatvars);
  }

  /** Holds the most recently computed summary value for this definition. */
  private int summary;

  /**
   * Points to a different definition with equivalent code, if one has been identified. A null value
   * indicates that there is no replacement.
   */
  private ClosureDefn replaceWith = null;

  ClosureDefn getReplaceWith() {
    return replaceWith;
  }

  /**
   * Look for a previously summarized version of this definition, returning true iff a duplicate was
   * found.
   */
  boolean findIn(ClosureDefns[] table) {
    summary = tail.summary();
    int idx = this.summary % table.length;
    if (idx < 0) {
      idx += table.length;
    }

    for (ClosureDefns ds = table[idx]; ds != null; ds = ds.next) {
      if (ds.head.summary == this.summary && ds.head.alphaClosureDefn(this)) {
        if (isEntrypoint) { // Cannot replace an entrypoint, even though a replacement is available
          return false;
        } else if (ds.head.declared == null
            || (this.declared != null && ds.head.declared.alphaEquiv(this.declared))) {
          MILProgram.report("Replacing " + this + " with " + ds.head);
          this.replaceWith = ds.head;
          return true;
        }
      }
    }

    // First sighting of this definition, add to the table:
    this.replaceWith = null; // There is no replacement for this definition (yet)
    table[idx] = new ClosureDefns(this, table[idx]);
    return false;
  }

  /**
   * Compute a summary for this definition (if it is a block, top-level, or closure) and then look
   * for a previously encountered item with the same code in the given table. Return true if a
   * duplicate was found.
   */
  boolean summarizeDefns(Blocks[] blocks, TopLevels[] topLevels, ClosureDefns[] closures) {
    return findIn(closures);
  }

  void eliminateDuplicates() {
    tail = tail.eliminateDuplicates();
  }

  /** Collect the set of types in this AST fragment and replace them with canonical versions. */
  void collect(TypeSet set) {
    if (declared != null) {
      declared = declared.canonAllocType(set);
    }
    if (defining != null) {
      defining = defining.canonAllocType(set);
    }
    Atom.collect(params, set);
    Atom.collect(args, set);
    tail.collect(set);
  }

  /** Apply constructor function simplifications to this program. */
  void cfunSimplify() {
    tail = tail.removeNewtypeCfun();
  }

  void printlnSig(PrintWriter out) {
    out.println(id + " :: " + declared);
  }

  /** Test to determine if this is an appropriate definition to match the given type. */
  ClosureDefn isClosureDefnOfType(AllocType inst) {
    return declared.alphaEquiv(inst) ? this : null;
  }

  ClosureDefn(ClosureDefn k, int num) {
    this(k.pos, mkid(k.id, num), null, null, null);
  }

  /**
   * Fill in the body of this definition as a specialized version of the given closure definition.
   */
  void specialize(MILSpec spec, ClosureDefn korig) {
    TVarSubst s = korig.declared.specializingSubst(korig.generics, this.declared);
    debug.Log.println(
        "ClosureDefn specialize: "
            + korig.getId()
            + " :: "
            + korig.declared
            + "  ~~>  "
            + this.getId()
            + " :: "
            + this.declared
            + ", generics="
            + TVar.show(korig.generics)
            + ", substitution="
            + s);
    this.params = Temp.specialize(s, korig.params);
    this.args = Temp.specialize(s, korig.args);
    SpecEnv env = new SpecEnv(korig.params, this.params, new SpecEnv(korig.args, this.args, null));
    this.tail = korig.tail.specializeTail(spec, s, env);
  }

  /**
   * Generate a specialized version of an entry point. This requires a monomorphic definition (to
   * ensure that the required specialization is uniquely determined, and to allow the specialized
   * version to share the same name as the original).
   */
  Defn specializeEntry(MILSpec spec) throws Failure {
    AllocType t = declared.isMonomorphic();
    if (t != null) {
      ClosureDefn e = spec.specializedClosureDefn(this, t);
      e.id = this.id; // use the same name as in the original program
      return e;
    }
    throw new PolymorphicEntrypointFailure("closure definition", this);
  }

  /** Update all declared types with canonical versions. */
  void canonDeclared(MILSpec spec) {
    declared = declared.canonAllocType(spec);
  }

  void bitdataRewrite(BitdataMap m) {
    tail = tail.bitdataRewrite(m);
  }

  void mergeRewrite(MergeMap mmap) {
    tail = tail.mergeRewrite(mmap);
  }

  /** Rewrite the components of this definition to account for changes in representation. */
  void repTransform(Handler handler, RepTypeSet set) {
    Temp[][] npss = Temp.reps(params); // analyze params
    RepEnv env = Temp.extend(params, npss, null); // environment for params
    params = Temp.repParams(params, npss);
    Temp[][] nass = Temp.reps(args); // analyze args
    env = Temp.extend(args, nass, env); // add environment for args
    args = Temp.repParams(args, nass);
    tail = tail.repTransform(set, env);
    declared = declared.canonAllocType(set);
  }

  /**
   * Perform scope analysis on a closure definition, creating new temporaries for each of the
   * (stored) parameters and input arguments, and checking that all of the identifiers in the given
   * tail have a corresponding binding.
   */
  public void inScopeOf(Handler handler, MILEnv milenv, String[] ids, String[] args, CodeExp cexp)
      throws Failure {
    this.params = Temp.makeTemps(ids.length);
    this.args = Temp.makeTemps(args.length);
    this.tail = cexp.toTail(handler, milenv, ids, this.params, args, this.args);
  }

  /** Add this exported definition to the specified MIL environment. */
  void addExport(MILEnv exports) {
    exports.addClosureDefn(id, this);
  }

  /**
   * Find the arguments that are needed to enter this definition. The arguments for each block or
   * closure are computed the first time that we visit the definition, but are returned directly for
   * each subsequent call.
   */
  public Temp[] addArgs() throws Failure {
    return params;
  }

  /** Returns the LLVM type for value that is returned by a function. */
  llvm.Type retType(LLVMMap lm) {
    return declared.resultType().retType(lm);
  }

  llvm.Type closurePtrType(LLVMMap lm) {
    return lm.toLLVM(declared.resultType());
  }

  llvm.Type codePtrType(LLVMMap lm) {
    return closurePtrType(lm).codePtrType();
  }

  /**
   * Calculate the type of a structure describing the layout of a closure for a specific definition.
   */
  llvm.Type closureLayoutTypeCalc(LLVMMap lm) {
    return declared.closureLayoutTypeCalc(lm);
  }

  llvm.Global closureGlobalCalc(LLVMMap lm) {
    return new llvm.Global(codePtrType(lm), functionName());
  }

  /** Return the name for the LLVM function corresponding to this definition. */
  String functionName() {
    return isEntrypoint ? id : ("clos_" + id);
  }

  /** Count the number of non-tail calls to blocks in this abstract syntax fragment. */
  void countCalls() {
    /* no non-tail calls here */
  }

  /**
   * Identify the set of blocks that should be included in the function that is generated for this
   * definition. A block call in the tail for a TopLevel is considered a regular call (it will
   * likely be called from the initialization code), but a block call in the tail for a ClosureDefn
   * is considered a genuine tail call. For a Block, we only search for the components of the
   * corresponding function if the block is the target of a call.
   */
  Blocks identifyBlocks() {
    return tail.identifyBlocks(this, null);
  }

  CFG makeCFG() {
    return new ClosureDefnCFG(this);
  }

  /** Find the CFG successors for this definition. */
  Label[] findSuccs(CFG cfg, Node src) {
    return tail.findSuccs(cfg, src);
  }

  /** Calculate an array of formal parameters for the associated LLVM function definition. */
  llvm.Local[] formals(LLVMMap lm, VarMap vm) {
    Temp[] nuargs = Temp.nonUnits(args);
    llvm.Local[] formals = new llvm.Local[1 + nuargs.length]; // Closure pointer + arguments
    formals[0] = vm.reg(closurePtrType(lm));
    for (int i = 0; i < nuargs.length; i++) {
      formals[1 + i] = vm.lookup(lm, nuargs[i]);
    }
    return formals;
  }

  /**
   * Construct a function definition with the given formal parameters and code, filling in an
   * appropriate code sequence for the entry block in cs[0], and setting the appropriate type and
   * internal flag values.
   */
  llvm.FuncDefn toLLVMFuncDefn(
      LLVMMap lm,
      DefnVarMap dvm,
      llvm.Local[] formals,
      String[] ss,
      llvm.Code[] cs,
      Label[] succs) {
    cs[0] =
        new llvm.CodeComment(
            "body of closure starts here", dvm.loadGlobals(tail.toLLVMDone(lm, dvm, null, succs)));
    Temp[] nuparams = Temp.nonUnits(params); // identify non unit parameters
    if (nuparams.length != 0) { // load closure parameters from memory
      llvm.Type ptrt = lm.closureLayoutType(this).ptr(); // type identifies components of closure
      llvm.Local ptr = dvm.reg(ptrt); // holds a pointer to the closure object
      for (int n = nuparams.length; --n >= 0; ) { // extract stored parameters
        llvm.Type pt = nuparams[n].lookupType(lm).ptr();
        llvm.Local pptr = dvm.reg(pt); // holds pointer to stored parameter
        cs[0] =
            new llvm.Op(
                pptr,
                new llvm.Getelementptr(pt, ptr, llvm.Word.ZERO, new llvm.Index(n + 1)),
                new llvm.Op(dvm.lookup(lm, nuparams[n]), new llvm.Load(pptr), cs[0]));
      }
      cs[0] =
          new llvm.CodeComment(
              "load stored values from closure",
              new llvm.Op(ptr, new llvm.Bitcast(formals[0], ptrt), cs[0]));
    }
    return new llvm.FuncDefn(
        llvm.Mods.entry(isEntrypoint), retType(lm), functionName(), formals, ss, cs);
  }
}
