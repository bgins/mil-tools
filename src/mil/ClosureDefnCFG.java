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

class ClosureDefnCFG extends CFG {

  private ClosureDefn k;

  /** Default constructor. */
  ClosureDefnCFG(ClosureDefn k) {
    this.k = k;
  }

  /** Return a string that can be used as the name of this node in debugging output. */
  String nodeName() {
    return k.functionName();
  }

  /** Return a string with the options (e.g., fillcolor) for displaying this CFG node. */
  String dotAttrs() {
    return k.dotAttrs();
  }

  void initCFG() {
    includedBlocks = k.identifyBlocks();
    succs = k.findSuccs(this, this);
    findSuccs();
  }

  /** Calculate an array of formal parameters for the associated LLVM function definition. */
  llvm.Local[] formals(LLVMMap lm, DefnVarMap dvm) {
    return k.formals(lm, dvm);
  }

  /**
   * Helper function for constructing a function definition with the fiven formal parameters and
   * code by connecting the the associated MIL Defn for additional details.
   */
  llvm.FuncDefn toLLVMFuncDefn(
      LLVMMap lm, DefnVarMap dvm, TempSubst s, llvm.Local[] formals, String[] ss, llvm.Code[] cs) {
    return k.toLLVMFuncDefn(lm, dvm, s, formals, ss, cs, succs);
  }
}
