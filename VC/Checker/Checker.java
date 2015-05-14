/**
 * Checker.java   
 * Sun Apr 26 13:41:38 AEST 2015
 **/

package VC.Checker;

import VC.ASTs.*;
import VC.Scanner.SourcePosition;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Checker implements Visitor {

	private String errMesg[] = {
			"*0: main function is missing",
			"*1: return type of main is not int",

			// defined occurrences of identifiers
			// for global, local and parameters
			"*2: identifier redeclared",
			"*3: identifier declared void",
			"*4: identifier declared void[]",

			// applied occurrences of identifiers
			"*5: identifier undeclared",

			// assignments
			"*6: incompatible type for =",
			"*7: invalid lvalue in assignment",

			// types for expressions
			"*8: incompatible type for return",
			"*9: incompatible type for this binary operator",
			"*10: incompatible type for this unary operator",

			// scalars
			"*11: attempt to use an array/fuction as a scalar",

			// arrays
			"*12: attempt to use a scalar/function as an array",
			"*13: wrong type for element in array initialiser",
			"*14: invalid initialiser: array initialiser for scalar",
			"*15: invalid initialiser: scalar initialiser for array",
			"*16: excess elements in array initialiser",
			"*17: array subscript is not an integer",
			"*18: array size missing",

			// functions
			"*19: attempt to reference a scalar/array as a function",

			// conditional expressions in if, for and while
			"*20: if conditional is not boolean",
			"*21: for conditional is not boolean",
			"*22: while conditional is not boolean",

			// break and continue
			"*23: break must be in a while/for",
			"*24: continue must be in a while/for",

			// parameters
			"*25: too many actual parameters",
			"*26: too few actual parameters",
			"*27: wrong type for actual parameter",

			// reserved for errors that I may have missed (J. Xue)
			"*28: misc 1", "*29: misc 2",

			// the following two checks are optional
			"*30: statement(s) not reached", "*31: missing return statement", };

	private SymbolTable idTable;
	private static SourcePosition dummyPos = new SourcePosition();
	private ErrorReporter reporter;

	// Checks whether the source program, represented by its AST,
	// satisfies the language's scope rules and type rules.
	// Also decorates the AST as follows:
	// (1) Each applied occurrence of an identifier is linked to
	// the corresponding declaration of that identifier.
	// (2) Each expression and variable is decorated by its type.

	boolean functionReturned;
	
	public Checker(ErrorReporter reporter) {
		this.reporter = reporter;
		this.idTable = new SymbolTable();
		establishStdEnvironment();
	}

	public void check(AST ast) {
		ast.visit(this, null);
	}

	// auxiliary methods

	private void declareVariable(Ident ident, Decl decl) {
		IdEntry entry = idTable.retrieveOneLevel(ident.spelling);
		if (entry == null) {
			; // no problem
		} else
			reporter.reportError(errMesg[2] + ": %", ident.spelling,
					ident.position);
		idTable.insert(ident.spelling, decl);
	}

	// Programs

	public Object visitProgram(Program ast, Object o) {
		ast.FL.visit(this, null);
		return null;
	}

	// Statements

	public Object visitCompoundStmt(CompoundStmt ast, Object o) {
		idTable.openScope();

		// Your code goes here
		ast.DL.visit(this, o);
		ast.SL.visit(this, o);
		idTable.closeScope();
		return null;
	}

	public Object visitStmtList(StmtList ast, Object o) {
		ast.S.visit(this, o);
		if (ast.S instanceof ReturnStmt && ast.SL instanceof StmtList)
			reporter.reportError(errMesg[30], "", ast.SL.position);
		ast.SL.visit(this, o);
		return null;
	}

	public void checkIsBooleanForConditional(Expr e, int errorCode){
		if(!e.type.isBooleanType()){
			reporter.reportError(errMesg[errorCode], "", e.position);
		}else{
		}
	}
	
	public Object visitIfStmt(IfStmt ast, Object o) {
		ast.E.visit(this, null);
		checkIsBooleanForConditional(ast.E, 20);
		ast.S1.visit(this, null);
		ast.S2.visit(this, null);
		return null;
	}
	
	public Object visitWhileStmt(WhileStmt ast, Object o) {
		ast.E.visit(this,null);
		checkIsBooleanForConditional(ast.E, 22);
		ast.S.visit(this,null);
		return null;
	}

	public Object visitForStmt(ForStmt ast, Object o) {
		ast.E1.visit(this,null);
		ast.E2.visit(this,null);// the conditional expression
		checkIsBooleanForConditional(ast.E2, 21);
		ast.E3.visit(this,null);
		ast.S.visit(this,null);
		return null;
	}

	public Object visitBreakStmt(BreakStmt ast, Object o) {
		return null;
	}

	public Object visitContinueStmt(ContinueStmt ast, Object o) {
		return null;
	}

	public Object visitReturnStmt(ReturnStmt ast, Object o) {
		// vist the return stmt
		ast.E.visit(this,null);
		// ensure the type is that of the func decl
		// check that they are equal
		Type funcType = ((FuncDecl)(o)).T;
		if(funcType.equals(ast.E.type)){
			functionReturned = true;
		}else if(funcType.assignable(ast.E.type)){
		// else assignable, 
			if (funcType.isFloatType() && ast.E.type.isIntType()) {
				// if assignable it has to be a int -> float
				ast.E = addUnaryFloatExpr(ast.E);
			}
			functionReturned = true;
		}else{
			reporter.reportError(errMesg[8], "", ast.position);
		}
		return null;
	}

	public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
		return null;
	}

	public Object visitExprStmt(ExprStmt ast, Object o) {
		ast.E.visit(this, o);
		return null;
	}

	public Object visitEmptyStmt(EmptyStmt ast, Object o) {
		return null;
	}

	public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
		return null;
	}

	// Expressions

	// Returns the Type denoting the type of the expression. Does
	// not use the given object.

	public Object visitEmptyExpr(EmptyExpr ast, Object o) {
		ast.type = StdEnvironment.errorType;
		return ast.type;
	}

	public Object visitBooleanExpr(BooleanExpr ast, Object o) {
		ast.type = StdEnvironment.booleanType;
		return ast.type;
	}

	public Object visitIntExpr(IntExpr ast, Object o) {
		ArrayType arrayObj = checkAndGetArrayType(o);
		ast.IL.visit(this,null);
		if(arrayObj != null){
			//make sure the int 
			
		}else {
			
		}
		ast.type = StdEnvironment.intType;

		return ast.type;
	}
	
	public IntLiteral getIntegerLitOfExpr(Expr expr){
		IntLiteral intLit = null;
		if(expr instanceof IntExpr){
			IntExpr intExpr = (IntExpr) expr;
			intLit = intExpr.IL;
		}
		return intLit;
	}
	
	public Object visitFloatExpr(FloatExpr ast, Object o) {
		ast.type = StdEnvironment.floatType;
		return ast.type;
	}

	public Object visitStringExpr(StringExpr ast, Object o) {
		ast.type = StdEnvironment.stringType;
		return ast.type;
	}

	public Object visitVarExpr(VarExpr ast, Object o) {
		ast.type = (Type) ast.V.visit(this, null);
		return ast.type;
	}

	public Object visitAssignExpr(AssignExpr ast, Object o) {
		// check if assignable
		ast.E1.visit(this, null);
		ast.E2.visit(this, null);
		if (ast.E1.type.assignable(ast.E2.type) ) {
			if (ast.E1.type.isFloatType() && ast.E2.type.isIntType()) {
				ast.E2 = addUnaryFloatExpr(ast.E2);
			}
		} else {
			reporter.reportError(errMesg[6], "", ast.position);
		}
		return ast.type;
	}

	public Object visitCallExpr(CallExpr ast, Object o) {
		return ast.type;
	}

	public Object visitArrayExpr(ArrayExpr ast, Object o) {
		return ast.type;
	}

	public Object visitExprList(ExprList ast, Object o) {
		ast.E.visit(this,o);
		// when it comes back, check the type and count
		if(o instanceof ArrayHelper){
			// check the type
			ArrayHelper arrHelp = (ArrayHelper)(o);
			if(ast.E.type.equals(arrHelp.getType())){
				//if valid increment coutn and check
				arrHelp.incrementCount();
				if(arrHelp.checkCount()){
				}else{
					reporter.reportError(errMesg[16], "", ast.position);
				}
			}else if(arrHelp.getType().assignable(ast.E.type)){
				// check if assignable
				if(checkIfFloatOpAndValid(ast.E.type, arrHelp.getType())){
					ast.E = addUnaryFloatExpr(ast.E);
				}
			}else{
				reporter.reportError(errMesg[13], "", ast.position);
			}
			
		}
		ast.EL.visit(this,o);
		return null;
	}

	public Object visitInitExpr(InitExpr ast, Object o) {
		// make sure the type is passed down
		// pass the etype back up
		ArrayType arrayObj = null;;
		if(o != null){
			arrayObj = checkAndGetArrayType(o);
			if(arrayObj != null){
				// set up the array checker
				ArrayHelper arrHelp;
				if(!arrayObj.E.type.isIntType()){
					arrHelp = new ArrayHelper(arrayObj.T,new EmptyExpr(dummyPos));
					reporter.reportError(errMesg[17], "", ast.position);
				}else{
					arrHelp = new ArrayHelper(arrayObj.T,arrayObj.E);
					
				}
				ast.IL.visit(this,arrHelp);
				// check if empty
				if(arrHelp.isEmptyExpr()){
					// fill the array type with an integer expr
					IntExpr expr = intExprFactory(arrHelp.getCount());
				    arrayObj.E = expr;
				}
			}
			ast.type = arrayObj;
		}else{
			reporter.reportError(errMesg[15], "", ast.position);
		}
		return ast.type;
	}
	
	public IntExpr intExprFactory(int value){
		IntLiteral intLit = new IntLiteral(Integer.toString(value), dummyPos);
		IntExpr eAST = new IntExpr(intLit, dummyPos);
		return eAST;
	}
	
	public ArrayType checkAndGetArrayType(Object o){
		ArrayType array = null;
		if(o instanceof ArrayType){
			array = (ArrayType) o;
		}
		return array;
	}

	public Object visitBinaryExpr(BinaryExpr ast, Object o) {
		ast.E1.visit(this, null);
		ast.E2.visit(this, null);
		// only boolean operators
		if(ast.O.spelling.equals("&&") || ast.O.spelling.equals("||") || ast.O.spelling.equals("!")){
			if(ast.E1.type.isBooleanType() && ast.E1.type.equals(ast.E2.type)){
				ast.O.spelling = "i" + ast.O.spelling;
				ast.type = ast.E1.type;
			}else{
				reporter.reportError(errMesg[9], "", ast.position);
			}
		}else{
		// the rest of them 
			if (ast.E1.type.equals(ast.E2.type)) {
				if (ast.E1.type.isIntType() || ast.E1.type.isBooleanType()) {
					ast.O.spelling = "i" + ast.O.spelling;
				} else if (ast.E1.type.isFloatType()) {
					ast.O.spelling = "f" + ast.O.spelling;
				}
				ast.type = ast.E1.type;
			} else if (checkIfFloatOpAndValid(ast.E1.type, ast.E2.type)) {
				ast.O.spelling = "f" + ast.O.spelling;
				ast.type = getTheFloatExpr(ast.E1, ast.E2).type;
				ast.E1 = addUnaryFloatExpr(ast.E1);
				ast.E2 = addUnaryFloatExpr(ast.E2);
			}else{
				reporter.reportError(errMesg[9], "", ast.position);
			}
			if(!checkNumericOperator(ast.O)){
			//if operators are not + - * or divide, change type to boolean
				ast.type = new BooleanType(dummyPos);
			}
		}
		return ast.type;
	}

	public boolean checkNumericOperator(Operator o){
		boolean isNumeric = false;
		if(o.spelling.contains("+") ||
				o.spelling.contains("-") ||
				o.spelling.contains("/") || 
				o.spelling.contains("*")){
			isNumeric = true;
		}
		return isNumeric;
	}
	
	public Expr addUnaryFloatExpr(Expr expr) {
		if (expr.type.isIntType()) {
			Operator op = new Operator("i2f", dummyPos);
			UnaryExpr eAST = new UnaryExpr(op, expr, dummyPos);
			eAST.type = StdEnvironment.floatType;
			expr = eAST;
		}
		return expr;
	}

	public Object visitUnaryExpr(UnaryExpr ast, Object o) {
		ast.E.visit(this, null);
		ast.O.spelling = "f" + ast.O.spelling;
		ast.type = ast.E.type;
		return ast.type;
	}

	public Expr getTheFloatExpr(Expr e1, Expr e2) {
		Expr expr = null;
		expr = e1.type.isFloatType() ? e1 : e2;
		return expr;
	}

	public boolean checkIfFloatOpAndValid(Type e1, Type e2) {
		// valid if either e1 or e2 is float
		boolean isFloatAndValid = false;
		if (e1.isFloatType()) {
			isFloatAndValid = e1.assignable(e2);
		} else if (e2.isFloatType()) {
			// the left over hast to be assignable
			isFloatAndValid = e2.assignable(e1);
		}
		return isFloatAndValid;
	}

	public Object visitEmptyExprList(EmptyExprList ast, Object o) {
		return null;
	}

	// Declarations

	// Always returns null. Does not use the given object.

	public Object visitFuncDecl(FuncDecl ast, Object o) {
		functionReturned = false;
		idTable.insert(ast.I.spelling, ast);
		// Your code goes here
		ast.PL.visit(this,ast);
		// HINT
		// Pass ast as the 2nd argument (as done below) so that the
		// formal parameters of the function an be extracted from ast when the
		// function body is later visited

		ast.S.visit(this, ast);
		// if fcuntion returned is false and type is not void throw error
		if(!ast.T.isVoidType() && functionReturned == false){
			reporter.reportError(errMesg[31], "", ast.position);
		}
		return null;
	}

	public Object visitDeclList(DeclList ast, Object o) {
		ast.D.visit(this, null);
		ast.DL.visit(this, null);
		return null;
	}

	public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
		return null;
	}

	public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
		declareVariable(ast.I, ast);
		ast.E.visit(this, null);
		// if type of ident and expr are equal then all good
		if (ast.T.assignable(ast.E.type)) {
			// if type of expr is int and ident is float then good
			if (ast.T.isFloatType() && ast.E.type.isIntType()) {
				Operator op = new Operator("i2f", dummyPos);
				UnaryExpr eAST = new UnaryExpr(op, ast.E, dummyPos);
				eAST.type = StdEnvironment.floatType;
				ast.E = eAST;
			}

		} else {
			// else no can DO BIATch
			reporter.reportError(errMesg[6], "", ast.position);
		}
		return null;
	}

	public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
		declareVariable(ast.I, ast);
		// if array type , we want to get the type of array
		if(ast.T.isArrayType()){
			// convert array type to array helper
			ast.E.type = (Type) ast.T.visit(this,null);
			
			ast.E.visit(this, ast.T);
		}else{
		//put the type into the expr
			ast.E.visit(this, null);
		}
		// if type of ident and expr are equal then all good
		if (ast.T.assignable(ast.E.type) || ast.T.isArrayType()) {
			// if type of expr is int and ident is float then good
			if (ast.T.isFloatType() && ast.E.type.isIntType()) {
				Operator op = new Operator("i2f", dummyPos);
				UnaryExpr eAST = new UnaryExpr(op, ast.E, dummyPos);
				eAST.type = StdEnvironment.floatType;
				ast.E = eAST;
			}
		} else {
			// else no can DO BIATch
			reporter.reportError(errMesg[6], "", ast.position);
		}
		return null;
	}

	// Parameters

	// Always returns null. Does not use the given object.

	public Object visitParaList(ParaList ast, Object o) {
		ast.P.visit(this, null);
		ast.PL.visit(this, null);
		return null;
	}

	public Object visitParaDecl(ParaDecl ast, Object o) {
		declareVariable(ast.I, ast);
		if (ast.T.isVoidType()) {
			reporter.reportError(errMesg[3] + ": %", ast.I.spelling,
					ast.I.position);
		} else if (ast.T.isArrayType()) {
			if (((ArrayType) ast.T).T.isVoidType())
				reporter.reportError(errMesg[4] + ": %", ast.I.spelling,
						ast.I.position);
		}
		return null;
	}

	public Object visitEmptyParaList(EmptyParaList ast, Object o) {
		return null;
	}

	// Arguments

	// Your visitor methods for arguments go here

	public Object visitArgList(ArgList ast, Object o) {
		return null;
	}

	public Object visitArg(Arg ast, Object o) {
		return null;
	}

	public Object visitEmptyArgList(EmptyArgList ast, Object o) {
		return null;
	}

	// Types

	// Returns the type predefined in the standard environment.

	public Object visitVoidType(VoidType ast, Object o) {
		return StdEnvironment.voidType;
	}

	public Object visitBooleanType(BooleanType ast, Object o) {
		return StdEnvironment.booleanType;
	}

	public Object visitErrorType(ErrorType ast, Object o) {
		return StdEnvironment.errorType;
	}

	public Object visitIntType(IntType ast, Object o) {
		return StdEnvironment.intType;
	}

	public Object visitFloatType(FloatType ast, Object o) {
		return StdEnvironment.floatType;
	}

	public Object visitStringType(StringType ast, Object o) {
		return StdEnvironment.stringType;
	}

	public Object visitArrayType(ArrayType ast, Object o) {
		ast.T.visit(this,null);
		ast.E.visit(this,null);
		return ast.T;
	}

	// Literals, Identifiers and Operators

	public Object visitIdent(Ident I, Object o) {
		Decl binding = idTable.retrieve(I.spelling);
		if (binding != null) {
			I.decl = binding;
		}
		return binding;
	}

	public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
		return StdEnvironment.booleanType;
	}

	public Object visitIntLiteral(IntLiteral IL, Object o) {
		return StdEnvironment.intType;
	}

	public Object visitFloatLiteral(FloatLiteral IL, Object o) {
		return StdEnvironment.floatType;
	}

	public Object visitStringLiteral(StringLiteral IL, Object o) {
		return StdEnvironment.stringType;
	}

	public Object visitOperator(Operator O, Object o) {
		return null;
	}

	// variables
	public Object visitSimpleVar(SimpleVar ast, Object o) {
		ast.I.visit(this, null);
		Decl decl = (Decl) ast.I.decl;
		if (ast.I.decl == null) {
			reporter.reportError(errMesg[5], "", ast.position);
			return StdEnvironment.errorType;
		}
		
		return decl.T;
	}

	// Creates a small AST to represent the "declaration" of each built-in
	// function, and enters it in the symbol table.

	private FuncDecl declareStdFunc(Type resultType, String id, List pl) {

		FuncDecl binding;

		binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl,
				new EmptyStmt(dummyPos), dummyPos);
		idTable.insert(id, binding);
		return binding;
	}
	
	

	// Creates small ASTs to represent "declarations" of all
	// build-in functions.
	// Inserts these "declarations" into the symbol table.

	private final static Ident dummyI = new Ident("x", dummyPos);

	private void establishStdEnvironment() {

		// Define four primitive types
		// errorType is assigned to ill-typed expressions

		StdEnvironment.booleanType = new BooleanType(dummyPos);
		StdEnvironment.intType = new IntType(dummyPos);
		StdEnvironment.floatType = new FloatType(dummyPos);
		StdEnvironment.stringType = new StringType(dummyPos);
		StdEnvironment.voidType = new VoidType(dummyPos);
		StdEnvironment.errorType = new ErrorType(dummyPos);

		// enter into the declarations for built-in functions into the table

		StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType,
				"getInt", new EmptyParaList(dummyPos));
		StdEnvironment.putIntDecl = declareStdFunc(StdEnvironment.voidType,
				"putInt", new ParaList(new ParaDecl(StdEnvironment.intType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.putIntLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putIntLn", new ParaList(new ParaDecl(StdEnvironment.intType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType,
				"getFloat", new EmptyParaList(dummyPos));
		StdEnvironment.putFloatDecl = declareStdFunc(StdEnvironment.voidType,
				"putFloat", new ParaList(new ParaDecl(StdEnvironment.floatType,
						dummyI, dummyPos), new EmptyParaList(dummyPos),
						dummyPos));
		StdEnvironment.putFloatLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putFloatLn", new ParaList(new ParaDecl(
						StdEnvironment.floatType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putBoolDecl = declareStdFunc(StdEnvironment.voidType,
				"putBool", new ParaList(new ParaDecl(
						StdEnvironment.booleanType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));
		StdEnvironment.putBoolLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putBoolLn", new ParaList(new ParaDecl(
						StdEnvironment.booleanType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));

		StdEnvironment.putStringLnDecl = declareStdFunc(
				StdEnvironment.voidType, "putStringLn", new ParaList(
						new ParaDecl(StdEnvironment.stringType, dummyI,
								dummyPos), new EmptyParaList(dummyPos),
						dummyPos));

		StdEnvironment.putStringDecl = declareStdFunc(StdEnvironment.voidType,
				"putString", new ParaList(new ParaDecl(
						StdEnvironment.stringType, dummyI, dummyPos),
						new EmptyParaList(dummyPos), dummyPos));

		StdEnvironment.putLnDecl = declareStdFunc(StdEnvironment.voidType,
				"putLn", new EmptyParaList(dummyPos));

	}
	private class ArrayHelper{
		//type 
		Type type;
		// count
		int count;
		// index
		int index;
		boolean empty;
		public ArrayHelper(Type t, Expr E){
			// convert the expression to an index
			index = 0;
			if(!(E instanceof EmptyExpr)){
				IntExpr expr = (IntExpr) E;
				index = Integer.parseInt(expr.IL.spelling);
				empty = false;
			}else{
				empty = true;
			}
			count = 0;
			type = t;
			
		}
		public void incrementCount(){
			count++;
		}
		
		public boolean checkCount(){
			boolean stillValid = false;
			if(count <= index || (index == 0 && empty)){
				stillValid = true;
			}
			return stillValid;
		}
		public Type getType(){
			return type;
		}
		
		public boolean isEmptyExpr(){
			return empty;
		}
		
		public int getCount(){
			return count;
		}
	}
}
