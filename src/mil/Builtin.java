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
import compiler.BuiltinPosition;
import compiler.Failure;
import core.*;

public class Builtin extends MILEnvHash {

  /** Default constructor. */
  public Builtin(TyconEnv tyconEnv) {
    super(tyconEnv);
  }

  public void print() {
    System.out.println("Builtin: --------");
    printThis();
  }

  private Builtin() {
    super(TyconEnv.builtin);
    try {
      // Add definitions for built-in constructor functions:
      addCfunAndTop(Cfun.Unit);
      addCfunAndTop(Cfun.Func); // TODO: maybe we shouldn't add the Top here ...
      addCfunAndTop(Cfun.Proc); // TODO: maybe we shouldn't add the Top here ...

      // Add definitions for built-in primitive functions:
      addNewPrim(Prim.not);
      addNewPrim(Prim.and);
      addNewPrim(Prim.or);
      addNewPrim(Prim.xor);
      addNewPrim(Prim.bnot);
      addNewPrim(Prim.shl);
      addNewPrim(Prim.lshr);
      addNewPrim(Prim.ashr);
      addNewPrim(Prim.neg);
      addNewPrim(Prim.add);
      addNewPrim(Prim.sub);
      addNewPrim(Prim.mul);
      addNewPrim(Prim.div);
      addNewPrim(Prim.eq);
      addNewPrim(Prim.neq);
      addNewPrim(Prim.lt);
      addNewPrim(Prim.lte);
      addNewPrim(Prim.gt);
      addNewPrim(Prim.gte);
      addNewPrim(Prim.flagToWord);
      addNewPrim(Prim.halt);
      addNewPrim(Prim.printWord);
      addNewPrim(Prim.loop);
      addNewPrim(Prim.load);
      addNewPrim(Prim.store);

    } catch (Exception e) { // Should this be a more specific exception?
      debug.Internal.error("Failed to initialize primitives");
    }

    // Add definitions for load and store primitives
    addPrim(Prim.load);
    addPrim(Prim.store);
  }

  public static final Builtin obj = new Builtin();

  /**
   * Add specified primitive to this MILEnv, triggering a failure if there is already a primitive
   * with the same name.
   */
  private void addNewPrim(Prim p) throws Failure {
    if (addPrim(p) != null) {
      // TODO: it would be good to give better position information for this primitive
      // TODO: it would be good to give position information for the other definition too
      multipleDefns(BuiltinPosition.position, "primitive", p.getId());
    }
  }
}
