/*
 * Emitter.java    15 -*- MAY -*- 2015
 * Jingling Xue, School of Computer Science, UNSW, Australia
 */

// A new frame object is created for every function just before the
// function is being translated in visitFuncDecl.
//
// All the information about the translation of a function should be
// placed in this Frame object and passed across the AST nodes as the
// 2nd argument of every visitor method in Emitter.java.

package VC.CodeGen;

import javax.naming.BinaryRefAddr;

import VC.ASTs.*;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Emitter implements Visitor {

	private ErrorReporter errorReporter;
	private String inputFilename;
	private String classname;
	private String outputFilename;

	public Emitter(String inputFilename, ErrorReporter reporter) {
		this.inputFilename = inputFilename;
		errorReporter = reporter;

		int i = inputFilename.lastIndexOf('.');
		if (i > 0)
			classname = inputFilename.substring(0, i);
		else
			classname = inputFilename;

	}

	// PRE: ast must be a Program node

	public final void gen(AST ast) {
		ast.visit(this, null);
		JVM.dump(classname + ".j");
	}

	// Programs
	public Object visitProgram(Program ast, Object o) {
		/**
		 * This method works for scalar variables only. You need to modify it to
		 * handle all array-related declarations and initialisations.
		 **/

		// Generates the default constructor initialiser
		emit(JVM.CLASS, "public", classname);
		emit(JVM.SUPER, "java/lang/Object");

		emit("");

		// Three subpasses:

		// (1) Generate .field definition statements since
		// these are required to appear before method definitions
		List list = ast.FL;
		while (!list.isEmpty()) {
			DeclList dlAST = (DeclList) list;
			if (dlAST.D instanceof GlobalVarDecl) {
				GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
				emit(JVM.STATIC_FIELD, vAST.I.spelling, VCtoJavaType(vAST.T));
			}
			list = dlAST.DL;
		}

		emit("");

		// (2) Generate <clinit> for global variables (assumed to be static)

		emit("; standard class static initializer ");
		emit(JVM.METHOD_START, "static <clinit>()V");
		emit("");

		// create a Frame for <clinit>

		Frame frame = new Frame(false);

		list = ast.FL;
		while (!list.isEmpty()) {
			DeclList dlAST = (DeclList) list;
			if (dlAST.D instanceof GlobalVarDecl) {
				GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
				if(vAST.T.isArrayType()){
//					dealWithArrType((ArrayType)(ast.T),o);
					vAST.T.visit(this,frame);
					// call the store command
				}
				if (!vAST.E.isEmptyExpr()) {
					vAST.E.visit(this, frame);
				} else {
					if (vAST.T.equals(StdEnvironment.floatType))
						emit(JVM.FCONST_0);
					else
						emit(JVM.ICONST_0);
					frame.push();
				}
				emitPUTSTATIC(VCtoJavaType(vAST.T), vAST.I.spelling);
				frame.pop();
			}
			list = dlAST.DL;
		}

		emit("");
		emit("; set limits used by this method");
		emit(JVM.LIMIT, "locals", frame.getNewIndex());

		emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
		emit(JVM.RETURN);
		emit(JVM.METHOD_END, "method");

		emit("");

		// (3) Generate Java bytecode for the VC program

		emit("; standard constructor initializer ");
		emit(JVM.METHOD_START, "public <init>()V");
		emit(JVM.LIMIT, "stack 1");
		emit(JVM.LIMIT, "locals 1");
		emit(JVM.ALOAD_0);
		emit(JVM.INVOKESPECIAL, "java/lang/Object/<init>()V");
		emit(JVM.RETURN);
		emit(JVM.METHOD_END, "method");

		return ast.FL.visit(this, o);
	}

	// Statements

	public Object visitStmtList(StmtList ast, Object o) {
		ast.S.visit(this, o);
		ast.SL.visit(this, o);
		return null;
	}

	public Object visitIfStmt(IfStmt ast, Object o) {
		Frame frame = (Frame) o;
		// grab two labels
		String lOne = frame.getNewLabel();
		String lTwo = frame.getNewLabel();
		
		ast.E.visit(this,o);
		checkRVar(ast.E, o);
		// emit the if eq statemetn
		
		emit(JVM.IFEQ,lOne);
		frame.pop();
		//visit s1
		ast.S1.visit(this,o);
		// emit go to
		emit(JVM.GOTO, lTwo);
		// l1 label
		emit(lOne + ":");
		//visit s2
		ast.S2.visit(this,o);
		//l2 label
		emit(lTwo + ":");
		return null;
	}

	public Object visitWhileStmt(WhileStmt ast, Object o) {
		Frame frame = (Frame) o;
		// get the two labels
		String lOne = frame.getNewLabel();
		String lTwo = frame.getNewLabel();
		//push lone to continue psuh ltwo to break
		frame.conStack.push(lOne);
		frame.brkStack.push(lTwo);
		emit(lOne + ":");
		ast.E.visit(this,o);
		emit(JVM.IFEQ,lTwo);
		ast.S.visit(this,o);
		emit(JVM.GOTO,lOne);
		emit(lTwo + ":");
		//pop the stack
		frame.conStack.pop();
		frame.brkStack.pop();
		return null;
	}

	public Object visitForStmt(ForStmt ast, Object o) {
		Frame frame = (Frame) o;
		// get two labels
		String lOne = frame.getNewLabel();
		String lTwo = frame.getNewLabel();
		frame.conStack.push(lOne);
		frame.brkStack.push(lTwo);
		//E1
		ast.E1.visit(this,o);
		//l1:
		emit(lOne + ":");
		// e2
		ast.E2.visit(this,o);
		// if eq l2
		emit(JVM.IFEQ,lTwo);
		frame.pop();
		// statemetn
		ast.S.visit(this,o);
		//e3
		ast.E3.visit(this,o);
		//gotol1
		emit(JVM.GOTO,lOne);
		//l2:
		emit(lTwo + ":");
		return null;
	}

	public Object visitBreakStmt(BreakStmt ast, Object o) {
		Frame frame = (Frame) o;
		String breakString = frame.brkStack.peek();
		emit(JVM.GOTO,breakString);
		return null;
	}

	public Object visitContinueStmt(ContinueStmt ast, Object o) {
		Frame frame = (Frame) o;
		String contString = frame.conStack.peek();
		emit(JVM.GOTO,contString);
		return null;
	}

	public Object visitCompoundStmt(CompoundStmt ast, Object o) {
		Frame frame = (Frame) o;

		String scopeStart = frame.getNewLabel();
		String scopeEnd = frame.getNewLabel();
		frame.scopeStart.push(scopeStart);
		frame.scopeEnd.push(scopeEnd);

		emit(scopeStart + ":");
		if (ast.parent instanceof FuncDecl) {
			if (((FuncDecl) ast.parent).I.spelling.equals("main")) {
				emit(JVM.VAR, "0 is argv [Ljava/lang/String; from "
						+ (String) frame.scopeStart.peek() + " to "
						+ (String) frame.scopeEnd.peek());
				emit(JVM.VAR, "1 is vc$ L" + classname + "; from "
						+ (String) frame.scopeStart.peek() + " to "
						+ (String) frame.scopeEnd.peek());
				// Generate code for the initialiser vc$ = new classname();
				emit(JVM.NEW, classname);
				emit(JVM.DUP);
				frame.push(2);
				emit("invokenonvirtual", classname + "/<init>()V");
				frame.pop();
				emit(JVM.ASTORE_1);
				frame.pop();
			} else {
				emit(JVM.VAR, "0 is this L" + classname + "; from "
						+ (String) frame.scopeStart.peek() + " to "
						+ (String) frame.scopeEnd.peek());
				((FuncDecl) ast.parent).PL.visit(this, o);
			}
		}
		ast.DL.visit(this, o);
		ast.SL.visit(this, o);
		emit(scopeEnd + ":");

		frame.scopeStart.pop();
		frame.scopeEnd.pop();
		return null;
	}

	boolean needToPop_;
	public Object visitExprStmt(ExprStmt ast, Object o) {
		Frame frame = (Frame) o;
		needToPop_ = true;
		ast.E.visit(this, o);
		if(needToPop_){
			frame.pop();
		}
		return null;
	}

	public Object visitReturnStmt(ReturnStmt ast, Object o) {
		Frame frame = (Frame) o;

		/*
		 * int main() { return 0; } must be interpretted as public static void
		 * main(String[] args) { return ; } Therefore, "return expr", if present
		 * in the main of a VC program must be translated into a RETURN rather
		 * than IRETURN instruction.
		 */

		if (frame.isMain()) {
			emit(JVM.RETURN);
			return null;
		}

		// if float return float
		// if inti have to cheick what the type of the funciton is;
		ast.E.visit(this,o);
		if(ast.E.type.isIntType() || ast.E.type.isBooleanType()){
			emit(JVM.IRETURN);
		}else if(ast.E.type.isFloatType()){
			emit(JVM.FRETURN);
		}
		return null;
	}

	public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
		return null;
	}

	public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
		return null;
	}

	public Object visitEmptyStmt(EmptyStmt ast, Object o) {
		return null;
	}

	// Expressions

	public Object visitCallExpr(CallExpr ast, Object o) {
		Frame frame = (Frame) o;
		String fname = ast.I.spelling;

		if (fname.equals("getInt")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System.getInt()I");
			frame.push();
		} else if (fname.equals("putInt")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System.putInt(I)V");
			frame.pop();
		} else if (fname.equals("putIntLn")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putIntLn(I)V");
			frame.pop();
		} else if (fname.equals("getFloat")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/getFloat()F");
			frame.push();
		} else if (fname.equals("putFloat")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putFloat(F)V");
			frame.pop();
		} else if (fname.equals("putFloatLn")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putFloatLn(F)V");
			frame.pop();
		} else if (fname.equals("putBool")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putBool(Z)V");
			frame.pop();
		} else if (fname.equals("putBoolLn")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putBoolLn(Z)V");
			frame.pop();
		} else if (fname.equals("putString")) {
			ast.AL.visit(this, o);
			emit(JVM.INVOKESTATIC,
					"VC/lang/System/putString(Ljava/lang/String;)V");
			frame.pop();
		} else if (fname.equals("putStringLn")) {
			ast.AL.visit(this, o);
			emit(JVM.INVOKESTATIC,
					"VC/lang/System/putStringLn(Ljava/lang/String;)V");
			frame.pop();
		} else if (fname.equals("putLn")) {
			ast.AL.visit(this, o); // push args (if any) into the op stack
			emit("invokestatic VC/lang/System/putLn()V");
			frame.pop();
		} else { // programmer-defined functions

			FuncDecl fAST = (FuncDecl) ast.I.decl;

			// all functions except main are assumed to be instance methods
			
			if (frame.isMain()){
				emit("aload_1"); // vc.funcname(...)
			}else
				emit("aload_0"); // this.funcname(...)
			frame.push();

			ast.AL.visit(this, o);

			String retType = VCtoJavaType(fAST.T);

			// The types of the parameters of the called function are not
			// directly available in the FuncDecl node but can be gathered
			// by traversing its field PL.

			StringBuffer argsTypes = new StringBuffer("");
			List fpl = fAST.PL;
			while (!fpl.isEmpty()) {
				if (((ParaList) fpl).P.T.equals(StdEnvironment.booleanType))
					argsTypes.append("Z");
				else if (((ParaList) fpl).P.T.equals(StdEnvironment.intType))
					argsTypes.append("I");
				else
					argsTypes.append("F");
				fpl = ((ParaList) fpl).PL;
			}

			emit("invokevirtual", classname + "/" + fname + "(" + argsTypes
					+ ")" + retType);
			System.out.println("to pop: " + (argsTypes.length() + 1));
			frame.pop(argsTypes.length() + 1);
			System.out.println("to pop: " + (argsTypes.length() + 1));
			if (!retType.equals("V")){
				frame.push();
			}
		}
		needToPop_ = false;
		return null;
	}

	public Object visitEmptyExpr(EmptyExpr ast, Object o) {
		return null;
	}

	public Object visitIntExpr(IntExpr ast, Object o) {
		ast.IL.visit(this, o);
		return null;
	}

	public Object visitFloatExpr(FloatExpr ast, Object o) {
		ast.FL.visit(this, o);
		return null;
	}

	public Object visitBooleanExpr(BooleanExpr ast, Object o) {
		ast.BL.visit(this, o);
		return null;
	}

	public Object visitStringExpr(StringExpr ast, Object o) {
		ast.SL.visit(this, o);
		return null;
	}

	public Object visitUnaryExpr(UnaryExpr ast, Object o) {
		ast.O.visit(this, o);
		ast.E.visit(this, o);
		checkRVar(ast.E, o);
		if (ast.O.spelling.equals("i2f")) {
			emit(JVM.I2F);
		} else if (ast.O.spelling.equals("i!")) {
			Frame frame = (Frame) o;
			// get to labels
			String lOne = frame.getNewLabel();
			String lTwo = frame.getNewLabel();
			emit(JVM.IFEQ,lOne);
			emit(JVM.ICONST_0);
			emit(JVM.GOTO,lTwo);
			emit(lOne + ":");
			emit(JVM.ICONST_1);
			emit(lTwo + ":");
		}
		return null;
	}

	public boolean isBinaryBool(Operator o) {
		boolean isBinary = false;
		if (o.spelling.equals("!") || o.spelling.equals("i&&")
				|| o.spelling.equals("i||")) {
			isBinary = true;
		}
		return isBinary;
	}

	public void printPrologue(String operator, boolean toPrint, String lOne, String lTwo) {
		if (toPrint) {
			if (operator.equals("i&&")) {
				emit(JVM.IFEQ, lOne);
			} else if (operator.equals("i||")) {
				emit(JVM.ICONST_1);
				emit(JVM.IF_ICMPEQ,lOne);
			} else if (operator.equals("i!")) {

			}
		}
	}

	public void printEpilogue(String operator, boolean toPrint, String lOne, String lTwo) {
		if (toPrint) {
			if (operator.equals("i&&")) {
				// &&
				emit(JVM.IFEQ, lOne);
				emit(JVM.ICONST_1);
				emit(JVM.GOTO, lTwo);
				emit(lOne + ":");
				emit(JVM.ICONST_0);
				emit(lTwo + ":");
			} else if (operator.equals("i||")) {
				emit(JVM.ICONST_1);
				emit(JVM.IF_ICMPEQ,lOne);
				emit(JVM.ICONST_0);
				emit(JVM.GOTO,lTwo);
				emit(lOne + ":");
				emit(JVM.ICONST_1);
				emit(lTwo + ":");
			} else if (operator.equals("i!")) {

			}
		}
	}

	public Object visitBinaryExpr(BinaryExpr ast, Object o) {
		Frame frame = (Frame) o;
		boolean isBoolean = false;
		boolean isBinaryBool = false;
		String lOne = "";
		String lTwo = "";
		String boolString = "";
		ast.O.visit(this, o);
		// check if the oeprator is ! , || , &&
		if (isBinaryBool(ast.O)) {
			isBinaryBool = true;
			// grab new labels
			lOne = frame.getNewLabel();
			lTwo = frame.getNewLabel();
		}
		// visit both left and right
		ast.E1.visit(this, o);
		printPrologue(ast.O.spelling, isBinaryBool,lOne,lTwo);
		checkRVar(ast.E1, o);
		ast.E2.visit(this, o);
		checkRVar(ast.E2, o);
		// visit the operator
		// check if E1 and E2 are var expressions
		// depending on the returning operator switch case
		if (ast.O.spelling.equals("i+")) {
			emit(JVM.IADD);
		} else if (ast.O.spelling.equals("i*")) {
			emit(JVM.IMUL);
		} else if(ast.O.spelling.equals("i/")){
			emit(JVM.IDIV);
		}else if(ast.O.spelling.equals("i-")){
			emit(JVM.ISUB);
		}else if (ast.O.spelling.equals("f+")) {
			emit(JVM.FADD);
		} else if (ast.O.spelling.equals("f*")) {
			emit(JVM.FMUL);
		}else if(ast.O.spelling.equals("f/")){
			emit(JVM.FDIV);
		}else if(ast.O.spelling.equals("f-")){
			emit(JVM.FSUB);
		} else if (ast.O.spelling.equals("f>")) {
			boolString = JVM.IFGT;
			isBoolean = true;
		} else if (ast.O.spelling.equals("f<")) {
			boolString = JVM.IFLT;
			isBoolean = true;
		}else if (ast.O.spelling.equals("f<=")) {
			boolString = JVM.IFLE;
			isBoolean = true;
		}else if (ast.O.spelling.equals("f>=")) {
			boolString = JVM.IFGE;
			isBoolean = true;
		} else if (ast.O.spelling.equals("i>")) {
			boolString = JVM.IF_ICMPGT;
			isBoolean = true;
		} else if (ast.O.spelling.equals("i>=")) {
			boolString = JVM.IF_ICMPGE;
			isBoolean = true;
		} else if (ast.O.spelling.equals("i<")) {
			boolString = JVM.IF_ICMPLT;
			isBoolean = true;
		} else if (ast.O.spelling.equals("i<=")) {
			boolString = JVM.IF_ICMPLE;
			isBoolean = true;
		} else if (ast.O.spelling.equals("i==")) {
			boolString = JVM.IF_ICMPEQ;
			isBoolean = true;
		}

		// check if is a boolean and grab the labesl
		if (isBoolean && ast.O.spelling.contains("i")) {
			// grab the labels
			lOne = frame.getNewLabel();
			lTwo = frame.getNewLabel();
			// emit the string with label one
			emit(boolString, lOne);
			// emit const 0
			emit(JVM.ICONST_0);
			// emit jump
			emit(JVM.GOTO, lTwo);
			// emit labelOne
			emit(lOne + ":");
			// emti const 1
			emit(JVM.ICONST_1);
			// emit labelTwo
			emit(lTwo + ":");
		}else if(isBoolean && ast.O.spelling.contains("f")){
			lOne = frame.getNewLabel();
			lTwo = frame.getNewLabel();
			// emit the string with label one
			emit(JVM.FCMPG);
			emit(boolString, lOne);
			// emit const 0
			emit(JVM.ICONST_0);
			// emit jump
			emit(JVM.GOTO, lTwo);
			// emit labelOne
			emit(lOne + ":");
			// emti const 1
			emit(JVM.ICONST_1);
			// emit labelTwo
			emit(lTwo + ":");
		}
		frame.pop();
		
		printEpilogue(ast.O.spelling, isBinaryBool, lOne, lTwo);
		return null;
	}

	public void checkRVar(Expr e, Object o) {
		// if is R var then call a load command for e
		Frame frame = (Frame) o;
		if (e instanceof VarExpr) {
			VarExpr varExp = (VarExpr) e;
			// check if it is a global decl or not
			int index = grabIndex(varExp);
			if(index != -1){
				if (varExp.V.type.isIntType()) {
					emitILOAD(index);
				} else if (varExp.V.type.isFloatType()) {
					emitFLOAD(index);
				} else if (varExp.V.type.isBooleanType()) {
					// booleans are loaded as ints
					emitILOAD(index);
				}
				// deal with an array load
				frame.push();
			}
		}else if(e instanceof ArrayExpr){
			ArrayExpr arrExpr = (ArrayExpr) e;
			// check what it is and load accordingly
			ArrayType arrType = (ArrayType) arrExpr.V.type;
			if (arrType.T.isIntType()) {
				emitIALOAD();
			} else if (arrType.T.isFloatType()) {
				emitFALOAD();
			} else if (arrType.T.isBooleanType()) {
				// booleans are loaded as ints
				emitBALOAD();
			}
			frame.push();
		}
		// if not rVar do nothing
	}

	public int grabIndex(VarExpr v) {
		// check if simple var
		int index = -1;
		if (v.V instanceof SimpleVar) {
			SimpleVar simVar = (SimpleVar) v.V;
			if (simVar.I.decl instanceof LocalVarDecl) {
				LocalVarDecl decl = (LocalVarDecl) simVar.I.decl;
				index = decl.index;
			}else if(simVar.I.decl instanceof ParaDecl){
				ParaDecl decl = (ParaDecl) simVar.I.decl;
				index = decl.index;
			}else if(simVar.I.decl instanceof GlobalVarDecl){
				GlobalVarDecl globDecl = (GlobalVarDecl) simVar.I.decl;
				emitGETSTATIC(VCtoJavaType(globDecl.T), globDecl.I.spelling);
			}
		}else{
		}
		return index;
	}

	private int grabIndexArr(ArrayExpr v){
		int index = -1;
		if (v.V instanceof SimpleVar) {
			SimpleVar simVar = (SimpleVar) v.V;
			if (simVar.I.decl instanceof LocalVarDecl) {
				LocalVarDecl decl = (LocalVarDecl) simVar.I.decl;
				index = decl.index;
			}
			else if(simVar.I.decl instanceof GlobalVarDecl){
				GlobalVarDecl globDecl = (GlobalVarDecl) simVar.I.decl;
				emitGETSTATIC(VCtoJavaType(globDecl.T), globDecl.I.spelling);
			}
		}
		return index;
	}

	int arrayCount_ = 0;
	public Object visitInitExpr(InitExpr ast, Object o) {
		arrayCount_ = 0;
		ast.IL.visit(this,o);
		arrayCount_ = 0;
		return null;
	}

	public Object visitExprList(ExprList ast, Object o) {
		Frame frame = (Frame) o;
		// if not the last exprresion emit a dup
		emit(JVM.DUP);
		// emit the index of store
		frame.push();
		emitICONST(arrayCount_);
		frame.push();
		arrayCount_++;
		// assume this is for an array
		ast.E.visit(this,o);
		// check the type of store and emit
		if(ast.E.type.isIntType()){
			emitIASTORE();
		}else if(ast.E.type.isFloatType()){
			emitFASTORE();
		}else if(ast.E.type.isBooleanType()){
			emitBASTORE();
		}
		frame.pop(3);
		ast.EL.visit(this,o);
		return null;
	}

	public Object visitArrayExpr(ArrayExpr ast, Object o) {
		//load the array 
		Frame frame = (Frame) o;
		int index = grabIndexArr(ast);
		if(index != -1){
			emitALOAD(grabIndexArr(ast));
			//jump here
			frame.push();
		}
		// load the index pointer
		ast.E.visit(this,o);
		// load 
		return null;
	}

	public Object visitVarExpr(VarExpr ast, Object o) {
		ast.V.visit(this, o);
		return null;
	}

	public Object visitAssignExpr(AssignExpr ast, Object o) {
		Frame frame = (Frame) o;
		ast.E1.visit(this, o);
		// must check if this is an array
		ast.E2.visit(this, o);
		// if e2 not an assign expression but parent is
		checkRVar(ast.E2, o);
		if(ast.parent instanceof AssignExpr && !(ast.E2 instanceof AssignExpr)){
			AssignExpr curAss = ast;
			while(curAss.parent instanceof AssignExpr){
				curAss = (AssignExpr)curAss.parent;
				emit(JVM.DUP);
				frame.push();
			}
		}
		// store instruction
		checkLVar(ast.E1,o);
		needToPop_ = false;
		return null;
	}

	public void checkLVar(Expr e, Object o) {
		Frame frame = (Frame) o;
		if (e instanceof VarExpr) {
			VarExpr varExp = (VarExpr) e;
			if (varExp.V.type.isIntType()) {
				emitISTORE(grabIdent(varExp));
			} else if (varExp.V.type.isFloatType()) {
				emitFSTORE(grabIdent(varExp));
			} else if (varExp.V.type.isBooleanType()) {
				// booleans are loaded as ints
				emitISTORE(grabIdent(varExp));
			}
			frame.pop();
		}else if(e instanceof ArrayExpr){
			// cehck what type we are
			ArrayExpr arrExp = (ArrayExpr) e;
			ArrayType arrType = (ArrayType) arrExp.V.type;
//			// call i/f/b/astore // this command pops two
			if (arrType.T.isIntType()) {
				emitIASTORE();
			} else if (arrType.T.isFloatType()) {
				emitFASTORE();
			} else if (arrType.T.isBooleanType()) {
				// booleans are loaded as ints
				emitBASTORE();
			}
			frame.pop(3);
		}
	}

	public Ident grabIdent(VarExpr v) {
		// check if simple var
		Ident retIdent = null;
		if (v.V instanceof SimpleVar) {
			SimpleVar simVar = (SimpleVar) v.V;
			retIdent = simVar.I;
		}
		return retIdent;
	}
	
	public Ident grabArrIdent(ArrayExpr arr){
		Ident retIdent = null;
		if(arr.V instanceof SimpleVar){
			SimpleVar simVar = (SimpleVar) arr.V;
			retIdent = simVar.I;
		}
		return retIdent;
	}

	public Object visitEmptyExprList(EmptyExprList ast, Object o) {
		return null;
	}

	// Declarations

	public Object visitDeclList(DeclList ast, Object o) {
		ast.D.visit(this, o);
		ast.DL.visit(this, o);
		return null;
	}

	public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
		return null;
	}

	public Object visitFuncDecl(FuncDecl ast, Object o) {

		Frame frame;

		if (ast.I.spelling.equals("main")) {
			frame = new Frame(true);

			// Assume that main has one String parameter and reserve 0 for it
			frame.getNewIndex();

			emit(JVM.METHOD_START, "public static main([Ljava/lang/String;)V");
			// Assume implicitly that
			// classname vc$;
			// appears before all local variable declarations.
			// (1) Reserve 1 for this object reference.

			frame.getNewIndex();

		} else {

			frame = new Frame(false);

			// all other programmer-defined functions are treated as if
			// they were instance methods
			frame.getNewIndex(); // reserve 0 for "this"

			String retType = VCtoJavaType(ast.T);

			// The types of the parameters of the called function are not
			// directly available in the FuncDecl node but can be gathered
			// by traversing its field PL.

			StringBuffer argsTypes = new StringBuffer("");
			List fpl = ast.PL;
			while (!fpl.isEmpty()) {
				if (((ParaList) fpl).P.T.equals(StdEnvironment.booleanType))
					argsTypes.append("Z");
				else if (((ParaList) fpl).P.T.equals(StdEnvironment.intType))
					argsTypes.append("I");
				else
					argsTypes.append("F");
				fpl = ((ParaList) fpl).PL;
			}

			emit(JVM.METHOD_START, ast.I.spelling + "(" + argsTypes + ")"
					+ retType);
		}

		ast.S.visit(this, frame);

		// JVM requires an explicit return in every method.
		// In VC, a function returning void may not contain a return, and
		// a function returning int or float is not guaranteed to contain
		// a return. Therefore, we add one at the end just to be sure.

		if (ast.T.equals(StdEnvironment.voidType)) {
			emit("");
			emit("; return may not be present in a VC function returning void");
			emit("; The following return inserted by the VC compiler");
			emit(JVM.RETURN);
		} else if (ast.I.spelling.equals("main")) {
			// In case VC's main does not have a return itself
			emit(JVM.RETURN);
		} else
			emit(JVM.NOP);

		emit("");
		emit("; set limits used by this method");
		emit(JVM.LIMIT, "locals", frame.getNewIndex());

		emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
		emit(".end method");

		return null;
	}

	public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
		// nothing to be done
		return null;
	}

	public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
		Frame frame = (Frame) o;
		ast.index = frame.getNewIndex();
		String T = VCtoJavaType(ast.T);

		emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T
				+ " from " + (String) frame.scopeStart.peek() + " to "
				+ (String) frame.scopeEnd.peek());
		// check the array type 
		if(ast.T.isArrayType()){
//			dealWithArrType((ArrayType)(ast.T),o);
			ast.T.visit(this,o);
			// call the store command
		}
		
		if (!ast.E.isEmptyExpr()) {
			ast.E.visit(this, o);
			checkRVar(ast.E, o);
			
			if (ast.T.equals(StdEnvironment.floatType)) {
				// cannot call emitFSTORE(ast.I) since this I is not an
				// applied occurrence
				if (ast.index >= 0 && ast.index <= 3)
					emit(JVM.FSTORE + "_" + ast.index);
				else
					emit(JVM.FSTORE, ast.index);
				frame.pop();
			} else if(ast.T.isArrayType()){
				
			}else {
				// cannot call emitISTORE(ast.I) since this I is not an
				// applied occurrence
				if (ast.index >= 0 && ast.index <= 3)
					emit(JVM.ISTORE + "_" + ast.index);
				else
					emit(JVM.ISTORE, ast.index);
				frame.pop();
			}
		}
		if(ast.T.isArrayType() ){
//			dealWithArrType((ArrayType)(ast.T),o);
			if (ast.index >= 0 && ast.index <= 3)
				emit(JVM.ASTORE + "_" + ast.index);
			else
				emit(JVM.ASTORE, ast.index);
			
			frame.pop(2);
		}

		return null;
	}

	// Parameters

	public Object visitParaList(ParaList ast, Object o) {
		ast.P.visit(this, o);
		ast.PL.visit(this, o);
		return null;
	}

	public Object visitParaDecl(ParaDecl ast, Object o) {
		Frame frame = (Frame) o;
		ast.index = frame.getNewIndex();
		String T = VCtoJavaType(ast.T);
		emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T
				+ " from " + (String) frame.scopeStart.peek() + " to "
				+ (String) frame.scopeEnd.peek());
		return null;
	}

	public Object visitEmptyParaList(EmptyParaList ast, Object o) {
		return null;
	}

	// Arguments

	public Object visitArgList(ArgList ast, Object o) {
		ast.A.visit(this, o);
		ast.AL.visit(this, o);
		return null;
	}

	public Object visitArg(Arg ast, Object o) {
		Frame frame = (Frame) o;
		ast.E.visit(this, o);
		// get the arg,  it'll be a var exp
		// check if it has a global var decl or normal var decl
		if(ast.E instanceof VarExpr){
			VarExpr varExpr = (VarExpr) ast.E;
			// ensure that the var is a simple var
			if(varExpr.V instanceof SimpleVar){
				// check if the simple var I Decl is global or local
				SimpleVar simVar = (SimpleVar) varExpr.V;
				if(simVar.I.decl instanceof LocalVarDecl){
					int index = -1;
					index = ((LocalVarDecl) simVar.I.decl).index;
					// args you want to load
					if(simVar.type.isIntType()){
						emitILOAD(index);
					}else if(simVar.type.isFloatType()){
						emitFLOAD(index);
					}else if(simVar.type.isBooleanType()){
						emitILOAD(index);
					}
					frame.push();
				}if(simVar.I.decl instanceof ParaDecl){
					int index = -1;
					index = ((ParaDecl) simVar.I.decl).index;
					// args you want to load
					if(simVar.type.isIntType()){
						emitILOAD(index);
					}else if(simVar.type.isFloatType()){
						emitFLOAD(index);
					}else if(simVar.type.isBooleanType()){
						emitILOAD(index);
					}
					frame.push();
				}else if(simVar.I.decl instanceof GlobalVarDecl){
					GlobalVarDecl decl = (GlobalVarDecl) simVar.I.decl;
					emit(JVM.GETSTATIC,classname + "/" + decl.I.spelling, VCtoJavaType(decl.T));
					frame.push();
				}
			}
		}
		return null;
	}

	public Object visitEmptyArgList(EmptyArgList ast, Object o) {
		return null;
	}

	// Types

	public Object visitIntType(IntType ast, Object o) {
		return null;
	}

	public Object visitFloatType(FloatType ast, Object o) {
		return null;
	}

	public Object visitBooleanType(BooleanType ast, Object o) {
		return null;
	}

	public Object visitVoidType(VoidType ast, Object o) {
		return null;
	}

	public Object visitStringType(StringType ast, Object o) {
		return null;
	}

	public Object visitArrayType(ArrayType ast, Object o) {
		//get the number of elements required,
		Frame frame = (Frame) o;
		ast.E.visit(this,o);
		// create new array object
		if(ast.T.isIntType()){
			emit(JVM.NEWARRAY,JVM.INTTYPE);
		}else if(ast.T.isFloatType()){
			emit(JVM.NEWARRAY,JVM.FLOATTYPE);
		}else if(ast.T.isBooleanType()){
			emit(JVM.NEWARRAY,JVM.BOOLTYPE);
		}
		frame.push();
		return null;
	}

	public Object visitErrorType(ErrorType ast, Object o) {
		return null;
	}

	// Literals, Identifiers and Operators

	public Object visitIdent(Ident ast, Object o) {
		return null;
	}

	public Object visitIntLiteral(IntLiteral ast, Object o) {
		Frame frame = (Frame) o;
		emitICONST(Integer.parseInt(ast.spelling));
		frame.push();
		return null;
	}

	public Object visitFloatLiteral(FloatLiteral ast, Object o) {
		Frame frame = (Frame) o;
		emitFCONST(Float.parseFloat(ast.spelling));
		frame.push();
		return null;
	}

	public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
		Frame frame = (Frame) o;
		emitBCONST(ast.spelling.equals("true"));
		frame.push();
		return null;
	}

	public Object visitStringLiteral(StringLiteral ast, Object o) {
		Frame frame = (Frame) o;
		emit(JVM.LDC, "\"" + ast.spelling + "\"");
		frame.push();
		return null;
	}

	public Object visitOperator(Operator ast, Object o) {
		return null;
	}

	// Variables

	public Object visitSimpleVar(SimpleVar ast, Object o) {
		return null;
	}

	// Auxiliary methods for byte code generation

	// The following method appends an instruction directly into the JVM
	// Code Store. It is called by all other overloaded emit methods.

	private void emit(String s) {
		JVM.append(new Instruction(s));
	}

	private void emit(String s1, String s2) {
		emit(s1 + " " + s2);
	}

	private void emit(String s1, int i) {
		emit(s1 + " " + i);
	}

	private void emit(String s1, float f) {
		emit(s1 + " " + f);
	}

	private void emit(String s1, String s2, int i) {
		emit(s1 + " " + s2 + " " + i);
	}

	private void emit(String s1, String s2, String s3) {
		emit(s1 + " " + s2 + " " + s3);
	}

	private void emitIF_ICMPCOND(String op, Frame frame) {
		String opcode;

		if (op.equals("i!="))
			opcode = JVM.IF_ICMPNE;
		else if (op.equals("i=="))
			opcode = JVM.IF_ICMPEQ;
		else if (op.equals("i<"))
			opcode = JVM.IF_ICMPLT;
		else if (op.equals("i<="))
			opcode = JVM.IF_ICMPLE;
		else if (op.equals("i>"))
			opcode = JVM.IF_ICMPGT;
		else
			// if (op.equals("i>="))
			opcode = JVM.IF_ICMPGE;

		String falseLabel = frame.getNewLabel();
		String nextLabel = frame.getNewLabel();

		emit(opcode, falseLabel);
		frame.pop(2);
		emit("iconst_0");
		emit("goto", nextLabel);
		emit(falseLabel + ":");
		emit(JVM.ICONST_1);
		frame.push();
		emit(nextLabel + ":");
	}

	private void emitFCMP(String op, Frame frame) {
		String opcode;

		if (op.equals("f!="))
			opcode = JVM.IFNE;
		else if (op.equals("f=="))
			opcode = JVM.IFEQ;
		else if (op.equals("f<"))
			opcode = JVM.IFLT;
		else if (op.equals("f<="))
			opcode = JVM.IFLE;
		else if (op.equals("f>"))
			opcode = JVM.IFGT;
		else
			// if (op.equals("f>="))
			opcode = JVM.IFGE;

		String falseLabel = frame.getNewLabel();
		String nextLabel = frame.getNewLabel();

		emit(JVM.FCMPG);
		frame.pop(2);
		emit(opcode, falseLabel);
		emit(JVM.ICONST_0);
		emit("goto", nextLabel);
		emit(falseLabel + ":");
		emit(JVM.ICONST_1);
		frame.push();
		emit(nextLabel + ":");

	}

	private void emitALOAD(int index) {
		if (index >= 0 && index <= 3)
			emit(JVM.ALOAD + "_" + index);
		else
			emit(JVM.ALOAD, index);
	}
	
	private void emitILOAD(int index) {
		if (index >= 0 && index <= 3)
			emit(JVM.ILOAD + "_" + index);
		else
			emit(JVM.ILOAD, index);
	}

	private void emitFLOAD(int index) {
		if (index >= 0 && index <= 3)
			emit(JVM.FLOAD + "_" + index);
		else
			emit(JVM.FLOAD, index);
	}
	
	private void emitIALOAD(){
		emit(JVM.IALOAD);
	}
	
	private void emitFALOAD(){
		emit(JVM.FALOAD);
	}
	
	private void emitBALOAD(){
		emit(JVM.BALOAD);
	}

	private void emitGETSTATIC(String T, String I) {
		emit(JVM.GETSTATIC, classname + "/" + I, T);
	}

	private void emitIASTORE() {
		emit(JVM.IASTORE);
	}
	private void emitFASTORE() {
		emit(JVM.FASTORE);
	}
	private void emitBASTORE() {
		emit(JVM.BASTORE);
	}
	
	private void emitISTORE(Ident ast) {
		int index;
		boolean globalVarDecl = false;
		if (ast.decl instanceof ParaDecl){
			index = ((ParaDecl) ast.decl).index;
		}else if(ast.decl instanceof LocalVarDecl){
			index = ((LocalVarDecl) ast.decl).index;
		}else if(ast.decl instanceof GlobalVarDecl){
			GlobalVarDecl decl = (GlobalVarDecl) ast.decl;
			emit(JVM.PUTSTATIC,classname + "/" + decl.I.spelling, VCtoJavaType(decl.T));
			globalVarDecl = true;
			index = -1;
		}else{
			index = -1;
		}
		if(!globalVarDecl){
			if (index >= 0 && index <= 3){
				emit(JVM.ISTORE + "_" + index);
			}else{
				emit(JVM.ISTORE, index);
			}
		}
	}

	private void emitFSTORE(Ident ast) {
		int index;
		if (ast.decl instanceof ParaDecl)
			index = ((ParaDecl) ast.decl).index;
		else
			index = ((LocalVarDecl) ast.decl).index;
		if (index >= 0 && index <= 3)
			emit(JVM.FSTORE + "_" + index);
		else
			emit(JVM.FSTORE, index);
	}

	private void emitPUTSTATIC(String T, String I) {
		emit(JVM.PUTSTATIC, classname + "/" + I, T);
	}

	private void emitICONST(int value) {
		if (value == -1)
			emit(JVM.ICONST_M1);
		else if (value >= 0 && value <= 5)
			emit(JVM.ICONST + "_" + value);
		else if (value >= -128 && value <= 127)
			emit(JVM.BIPUSH, value);
		else if (value >= -32768 && value <= 32767)
			emit(JVM.SIPUSH, value);
		else
			emit(JVM.LDC, value);
	}

	private void emitFCONST(float value) {
		if (value == 0.0)
			emit(JVM.FCONST_0);
		else if (value == 1.0)
			emit(JVM.FCONST_1);
		else if (value == 2.0)
			emit(JVM.FCONST_2);
		else
			emit(JVM.LDC, value);
	}

	private void emitBCONST(boolean value) {
		if (value)
			emit(JVM.ICONST_1);
		else
			emit(JVM.ICONST_0);
	}

	private String VCtoJavaType(Type t) {
		if (t.equals(StdEnvironment.booleanType))
			return "Z";
		else if (t.equals(StdEnvironment.intType))
			return "I";
		else if (t.equals(StdEnvironment.floatType))
			return "F";
		else if (t.isArrayType())
			return getArrayType((ArrayType)t);
		else
			// if (t.equals(StdEnvironment.voidType))
			return "V";
	}
	
	private String getArrayType(ArrayType t){
		if(t.T.isIntType()){
			return "[I";
		}else if(t.T.isFloatType()){
			return "[F";
		}else if(t.T.isBooleanType()){
			return "[Z";
		}else{
			return "V";
		}
	}
	

}
