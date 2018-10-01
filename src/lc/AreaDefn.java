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
package lc;

import compiler.*;
import core.*;
import mil.*;

public class AreaDefn extends TopDefn {

  private AreaVar[] areas;

  private TypeExp typeExp;

  private TypeExp alignExp;

  /** Default constructor. */
  public AreaDefn(Position pos, AreaVar[] areas, TypeExp typeExp, TypeExp alignExp) {
    super(pos);
    this.areas = areas;
    this.typeExp = typeExp;
    this.alignExp = alignExp;
  }

  private Type initType;

  /**
   * Validate this top level definition and add corresponding entries to the environment, if
   * necessary.
   */
  void validateTopDefn(Handler handler, MILEnv milenv) throws Failure {
    try {
      // Validate the area type:
      typeExp.scopeType(null, milenv.getTyconEnv(), 0);
      typeExp.checkKind(KAtom.STAR);
      Type refType = typeExp.toType(null); // The type of area reference, as declared
      Type areaType = new TVar(Tyvar.area);
      if (!Type.ref(areaType).match(null, refType, null)) {
        throw new Failure(typeExp.position(), "area definition requires a reference type");
      }
      areaType = areaType.skeleton();

      // Determine alignment, validating declared value if given:
      long alignment = areaType.calcAlignment(pos, milenv, alignExp);
      debug.Log.println("area type is " + areaType + ", alignment=" + alignment);

      initType = Type.init(areaType); // Calculate and save type for initializers
      for (int i = 0; i < areas.length; i++) {
        areas[i].addToEnv(handler, milenv, alignment, areaType, refType);
      }
    } catch (Failure f) {
      handler.report(f);
    }
  }

  /**
   * Run scope analysis on a top level lc definition to ensure that all the items identified as
   * exports or entrypoints are in scope, either as a binding in this program, or as a Top that is
   * visible in the current environment.
   */
  void scopeTopDefn(Handler handler, MILEnv milenv, Env env) throws Failure {
    for (int i = 0; i < areas.length; i++) {
      areas[i].scopeTopDefn(handler, milenv, env);
    }
  }

  /** Check types of expressions appearing in top-level definitions. */
  void inferTypes(Handler handler) throws Failure {
    for (int i = 0; i < areas.length; i++) {
      areas[i].inferTypes(handler, initType);
    }
  }

  void liftTopDefn(LiftEnv lenv) {
    for (int i = 0; i < areas.length; i++) {
      areas[i].liftTopDefn(lenv);
    }
  }

  /** Generate code, if necessary, for top-level definitions. */
  void compileTopDefn() {
    for (int i = 0; i < areas.length; i++) {
      areas[i].compileAreaVar(initType);
    }
  }

  void addExports(MILProgram mil, MILEnv milenv) {
    /* Nothing to do here! */
  }
}
