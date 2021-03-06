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

class PrimDefnExp extends DefnExp {

  private String id;

  private int purity;

  private boolean makechain;

  private boolean thunk;

  private TupleTypeExp domtuple;

  private TupleTypeExp rngtuple;

  /** Default constructor. */
  PrimDefnExp(
      Position pos,
      String id,
      int purity,
      boolean makechain,
      boolean thunk,
      TupleTypeExp domtuple,
      TupleTypeExp rngtuple) {
    super(pos);
    this.id = id;
    this.purity = purity;
    this.makechain = makechain;
    this.thunk = thunk;
    this.domtuple = domtuple;
    this.rngtuple = rngtuple;
  }

  private TopLevel t;

  private Prim prim;

  /**
   * Worker function for addTo(handler, milenv) that throws an exception if an error is detected.
   */
  void addTo(MILEnv milenv) throws Failure {
    BlockType bt = BlockType.validate(milenv.getTyconEnv(), domtuple, rngtuple);
    prim = new Prim(id, purity, bt);
    debug.Log.println("primitive " + id + " :: " + bt);
    if (milenv.addPrim(prim) != null) {
      MILEnv.multipleDefns(pos, "primitive", id);
    }
    if (makechain) {
      t = new TopLevel(pos, id, prim.maker(pos, thunk));
      if (milenv.addTop(id, new TopDef(t, 0)) != null) {
        MILEnv.multipleDefns(pos, "primitive function", id);
      }
    }
  }

  /**
   * Perform scope analysis on this definition to ensure that all referenced identifiers are
   * appropriately bound.
   */
  void inScopeOf(Handler handler, MILEnv milenv) throws Failure {
    /* nothing to do here */
  }

  /**
   * Add the MIL definition associated with this DefnExp, if any, as an entrypoint to the specified
   * program.
   */
  void addAsEntryTo(MILProgram mil) {
    if (t != null) mil.addEntry(t);
  }
}
