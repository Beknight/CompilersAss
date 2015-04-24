/*
 * Parser.java            
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  [1] a list (of statements)
 *  [2] a function
 *  [3] a statement (which is an expression statement), 
 *  [4] a unary expression
 *  [5] a binary expression
 *  [6] terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * (10-*-April-*-2015)


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Parser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

public class Parser {

	private Scanner scanner;
	private ErrorReporter errorReporter;
	private Token currentToken;
	private SourcePosition previousTokenPosition;
	private SourcePosition dummyPos = new SourcePosition();

	public Parser(Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		previousTokenPosition = new SourcePosition();

		currentToken = scanner.getToken();
	}

	// match checks to see f the current token matches tokenExpected.
	// If so, fetches the next token.
	// If not, reports a syntactic error.

	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			previousTokenPosition = currentToken.position;
			currentToken = scanner.getToken();
		} else {
			syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
	}

	void accept() {
		previousTokenPosition = currentToken.position;
		currentToken = scanner.getToken();
	}

	void syntacticError(String messageTemplate, String tokenQuoted)
			throws SyntaxError {
		SourcePosition pos = currentToken.position;
		errorReporter.reportError(messageTemplate, tokenQuoted, pos);
		throw (new SyntaxError());
	}

	// start records the position of the start of a phrase.
	// This is defined to be the position of the first
	// character of the first token of the phrase.

	void start(SourcePosition position) {
		position.lineStart = currentToken.position.lineStart;
		position.charStart = currentToken.position.charStart;
	}

	// finish records the position of the end of a phrase.
	// This is defined to be the position of the last
	// character of the last token of the phrase.

	void finish(SourcePosition position) {
		position.lineFinish = previousTokenPosition.lineFinish;
		position.charFinish = previousTokenPosition.charFinish;
	}

	void copyStart(SourcePosition from, SourcePosition to) {
		to.lineStart = from.lineStart;
		to.charStart = from.charStart;
	}

	// ========================== PROGRAMS ========================

	public Program parseProgram() {

		Program programAST = null;

		SourcePosition programPos = new SourcePosition();
		start(programPos);

		try {
			List dlAST = parseFuncDeclList();
			finish(programPos);
			programAST = new Program(dlAST, programPos);
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" unknown type", currentToken.spelling);
			}
		} catch (SyntaxError s) {
			return null;
		}
		return programAST;
	}

	// ========================== DECLARATIONS ========================

	List parseFuncDeclList() throws SyntaxError {
		List dlAST = null;
		Decl dAST = null;

		SourcePosition funcPos = new SourcePosition();
		start(funcPos);

		dAST = parseFuncDecl();

		if (currentToken.kind == Token.VOID) {
			dlAST = parseFuncDeclList();
			finish(funcPos);
			dlAST = new DeclList(dAST, dlAST, funcPos);
		} else if (dAST != null) {
			finish(funcPos);
			dlAST = new DeclList(dAST, new EmptyDeclList(dummyPos), funcPos);
		}
		if (dlAST == null)
			dlAST = new EmptyDeclList(dummyPos);

		return dlAST;
	}

	Decl parseGlobalVarDecl() throws SyntaxError{
		Decl vAST = null;
		SourcePosition varPos = new SourcePosition();
		start(varPos);
		Type tAST = parseType();
		// decl list
		
		// initialiser
	}
	
	Decl parseFuncDecl() throws SyntaxError {

		Decl fAST = null;

		SourcePosition funcPos = new SourcePosition();
		start(funcPos);

		Type tAST = parseType();
		Ident iAST = parseIdent();
		List fplAST = parseParaList();
		Stmt cAST = parseCompoundStmt();
		finish(funcPos);
		fAST = new FuncDecl(tAST, iAST, fplAST, cAST, funcPos);
		return fAST;
	}

	Decl parseDecl() throws SyntaxError{
		Decl decl = null;
		SourcePosition declPos = new SourcePosition;
		start(declPos);
		//parse ident
		Ident id = parseIdent();
		IntLiteral intLit = null;
		//(parseDecl)?
		if(currentToken.kind == Token.LBRACKET){
			match(Token.LBRACKET);
			if(currentToken.kind == Token.INTLITERAL){
				intLit = parseIntLiteral();
			}
			match(Token.RBRACKET);
		}
		decl = new Decl(declPos);
		return decl;
	}
	
    InitExpr parseInitialiser() throws SyntaxError{
    	InitExpr iExpr = null;
    	ExprList eList = null;
    	SourcePosition initPos = new SourcePosition();
    	start(initPos);
    	if(currentToken.kind != Token.LCURLY){
    		// parse expr
    		Expr expr parseExpr();
    		// built the list
    		eList = new ExprList(expr,eList,initPos);
    		//finish
    		finish(initPos);
    	}else{
    		// match curly
    		match(Token.LCURLY);
    		//parse expr
    		Expr expr = parseExpr();
    		//build list
    		eList = new ExprList(expr, eList,initPos)
    		//while comma
    		while(currentToken.kind = Token.COMMA){
    			//parse expr
    			expr = parseExpr();
        		//build list
        		eList = new ExprList(expr, eList,initPos)
    		}
    		finish(initPos);
    		// close curly
    		match(Token.RCURLY);
    	}
    	iExpr = new InitExpr(eList,initPos);
    	return iExpr;
    }
	
	// ======================== TYPES ==========================

	Type parseType() throws SyntaxError {
		Type typeAST = null;

		SourcePosition typePos = new SourcePosition();
		start(typePos);

		accept();
		finish(typePos);
		typeAST = new VoidType(typePos);

		return typeAST;
	}

	// ======================= STATEMENTS ==============================

	Stmt parseCompoundStmt() throws SyntaxError {
		Stmt cAST = null;

		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);

		match(Token.LCURLY);

		// Insert code here to build a DeclList node for variable declarations
		List slAST = parseStmtList();
		match(Token.RCURLY);
		finish(stmtPos);

		/*
		 * In the subset of the VC grammar, no variable declarations are
		 * allowed. Therefore, a block is empty iff it has no statements.
		 */
		if (slAST instanceof EmptyStmtList)
			cAST = new EmptyCompStmt(stmtPos);
		else
			cAST = new CompoundStmt(new EmptyDeclList(dummyPos), slAST, stmtPos);
		return cAST;
	}

	List parseStmtList() throws SyntaxError {
		List slAST = null;

		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);

		if (currentToken.kind != Token.RCURLY) {
			Stmt sAST = parseStmt();
			{
				if (currentToken.kind != Token.RCURLY) {
					slAST = parseStmtList();
					finish(stmtPos);
					slAST = new StmtList(sAST, slAST, stmtPos);
				} else {
					finish(stmtPos);
					slAST = new StmtList(sAST, new EmptyStmtList(dummyPos),
							stmtPos);
				}
			}
		} else
			slAST = new EmptyStmtList(dummyPos);

		return slAST;
	}

	Stmt parseStmt() throws SyntaxError {
		Stmt sAST = null;

		sAST = parseExprStmt();

		return sAST;
	}

	Stmt parseExprStmt() throws SyntaxError {
		Stmt sAST = null;

		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);

		if (currentToken.kind == Token.ID
				|| currentToken.kind == Token.INTLITERAL
				|| currentToken.kind == Token.LPAREN) {
			Expr eAST = parseExpr();
			match(Token.SEMICOLON);
			finish(stmtPos);
			sAST = new ExprStmt(eAST, stmtPos);
		} else {
			match(Token.SEMICOLON);
			finish(stmtPos);
			sAST = new ExprStmt(new EmptyExpr(dummyPos), stmtPos);
		}
		return sAST;
	}

	// ======================= PARAMETERS =======================

	List parseParaList() throws SyntaxError {
		List formalsAST = null;

		SourcePosition formalsPos = new SourcePosition();
		start(formalsPos);

		match(Token.LPAREN);
		if (currentToken.kind == Token.RPAREN) {
			match(Token.RPAREN);
			finish(formalsPos);
			formalsAST = new EmptyParaList(formalsPos);
		} else {

			// match(Token.RPAREN);
			// finish(formsPos);
			// formalsAST = new ParaList();
		}
		return formalsAST;
	}
	
	List parseProperParaList() throws SyntaxError{
		List propParaList = null;
		SourcePosition properParaListStart = new SourcePosition();
		start(properParaListStart);
		ParaDecl pAST= parseParaDecl();
		propParaList = new ParaList(pAST,propParaList,properParaListStart);
		while(currentToken.kind == Token.COMMA){
			accept();
			pAST = parseParaDecl();
			SourcePosition properParaListPos = new SourcePosition();
			copyStart(properParaListStart,properParaListPos);
			finish(properParaListPos);
			propParaList = new ParaList(pAST,propParaList,properParaListPos);
		}
		return propParaList;
	}
	
	ParaDecl parseParaDecl() throws SyntaxError{
		ParaDecl decl = null;
		SourcePosition paraDeclStart = new SourcePosition();
		//parse type
		
		//parse declarator
		
		finish(paraDeclStart);
		// create decl object
		
		return decl;
	}

	List parseArgList() throws SyntaxError{
		List argList = null;
		SourcePosition argListStartPos = new SourcePosition();
		start(argListStartPos);
		match(Token.LPAREN);
		if(currentToken.kind == Token.RPAREN){
			match(Token.RPAREN);
			finish(argListStartPos);
			argList = new EmptyArgList(argListStartPos);
		}else{
			argList = parseProperArgList();
			finish(argListStartPos);
		}
		return argList;
	}

	List parseProperArgList() throws SyntaxError {
		List argList = null;
		SourcePosition propArgListPosStart = new SourcePosition();
		start(propArgListPosStart);
		Arg arg = parseArg();
		argList = new ArgList(arg, argList, propArgListPosStart);
		while (currentToken.kind == Token.COMMA) {
			accept();
			arg = parseArg();
			SourcePosition propArgList = new SourcePosition();
			copyStart(propArgListPosStart, propArgList);
			finish(propArgList);
			argList = new ArgList(arg, argList, propArgList);
		}
		return argList;
	}

	Arg parseArg() throws SyntaxError {
		// parse EXPR
		Arg arg = null;
		SourcePosition parseArgStart = new SourcePosition();
		Expr expr = parseExpr();
		finish(parseArgStart);
		arg = new Arg(expr, parseArgStart);
		return arg;
	}

	// ======================= EXPRESSIONS ======================

	Expr parseExpr() throws SyntaxError {
		Expr exprAST = null;
		exprAST = parseAdditiveExpr();
		return exprAST;
	}

	Expr parseAssignExpr() throws SyntaxError {
		Expr exprAST = null;
		SourcePosition assignExprStart = new SourcePosition();
		start(assignExprStart);
		exprAST = parseCondOrExpr();
		while (currentToken.kind == Token.EQ) {
			Operator opAST = acceptOperator();
			Expr e2AST = parseCondOrExpr();
			SourcePosition assignPos = new SourcePosition();
			copyStart(assignExprStart, assignPos);
			finish(assignPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, assignPos);
		}

		return exprAST;
	}

	Expr parseCondOrExpr() throws SyntaxError {
		Expr exprAST = null;
		SourcePosition condOrExprStart = new SourcePosition();
		start(condOrExprStart);
		exprAST = parseCondAndExpr();
		while (currentToken.kind == Token.OROR) {
			// accept operator
			Operator opAST = acceptOperator();
			// prase conad and expr
			Expr e2AST = parseCondAndExpr();
			// start new pos
			SourcePosition condOrPos = new SourcePosition();
			copyStart(condOrExprStart, condOrPos);
			finish(condOrPos);
			// parse info into expr tree
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, condOrPos);
		}
		// return expr
		return exprAST;
	}

	Expr parseCondAndExpr() throws SyntaxError {
		Expr exprAST = null;
		SourcePosition condAndStartPos = new SourcePosition();
		start(condAndStartPos);
		exprAST = parseEqualityExpr();
		while (currentToken.kind == Token.ANDAND) {
			Operator opAST = acceptOperator();
			// parse equality expr
			Expr e2AST = parseEqualityExpr();
			SourcePosition condAndPos = new SourcePosition();
			copyStart(condAndStartPos, condAndPos);
			finish(condAndPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, condAndPos);
		}
		return exprAST;
	}

	Expr parseEqualityExpr() throws SyntaxError {
		Expr exprAST = null;
		SourcePosition equStartPos = new SourcePosition();
		start(equStartPos);
		exprAST = parseRelExpr();
		while (currentToken.kind == Token.EQEQ
				|| currentToken.kind == Token.NOTEQ) {
			Operator opAST = acceptOperator();
			// parse rel expr
			Expr e2AST = parseRelExpr();
			SourcePosition eqPos = new SourcePosition();
			copyStart(equStartPos, eqPos);
			finish(eqPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, eqPos);
		}
		return exprAST;
	}

	Expr parseRelExpr() throws SyntaxError {
		Expr exprAST = null;
		SourcePosition relStartPos = new SourcePosition();
		start(relStartPos);
		exprAST = parseAdditiveExpr();
		while (currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ
				|| currentToken.kind == Token.LT
				|| currentToken.kind == Token.LTEQ) {
			Operator opAST = acceptOperator();
			// parse additive
			Expr e2AST = parseAdditiveExpr();
			SourcePosition relPos = new SourcePosition();
			copyStart(relStartPos, relPos);
			finish(relPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, relPos);
		}
		return exprAST;
	}

	Expr parseAdditiveExpr() throws SyntaxError {
		Expr exprAST = null;

		SourcePosition addStartPos = new SourcePosition();
		start(addStartPos);

		exprAST = parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS
				|| currentToken.kind == Token.MINUS) {
			Operator opAST = acceptOperator();
			Expr e2AST = parseMultiplicativeExpr();

			SourcePosition addPos = new SourcePosition();
			copyStart(addStartPos, addPos);
			finish(addPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, addPos);
		}
		return exprAST;
	}

	Expr parseMultiplicativeExpr() throws SyntaxError {

		Expr exprAST = null;

		SourcePosition multStartPos = new SourcePosition();
		start(multStartPos);

		exprAST = parseUnaryExpr();
		while (currentToken.kind == Token.MULT
				|| currentToken.kind == Token.DIV) {
			Operator opAST = acceptOperator();
			Expr e2AST = parseUnaryExpr();
			SourcePosition multPos = new SourcePosition();
			copyStart(multStartPos, multPos);
			finish(multPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, multPos);
		}
		return exprAST;
	}

	Expr parseUnaryExpr() throws SyntaxError {

		Expr exprAST = null;

		SourcePosition unaryPos = new SourcePosition();
		start(unaryPos);

		switch (currentToken.kind) {
		case Token.MINUS:
		case Token.PLUS:
		case Token.NOT: {
			Operator opAST = acceptOperator();
			Expr e2AST = parseUnaryExpr();
			finish(unaryPos);
			exprAST = new UnaryExpr(opAST, e2AST, unaryPos);
		}
			break;

		default:
			exprAST = parsePrimaryExpr();
			break;

		}
		return exprAST;
	}

	Expr parsePrimaryExpr() throws SyntaxError {

		Expr exprAST = null;

		SourcePosition primPos = new SourcePosition();
		start(primPos);

		switch (currentToken.kind) {

		case Token.ID:
			Ident iAST = parseIdent();
			finish(primPos);
			Var simVAST = new SimpleVar(iAST, primPos);
			exprAST = new VarExpr(simVAST, primPos);
			break;

		case Token.LPAREN: {
			accept();
			exprAST = parseExpr();
			match(Token.RPAREN);
		}
			break;

		case Token.INTLITERAL:
			IntLiteral ilAST = parseIntLiteral();
			finish(primPos);
			exprAST = new IntExpr(ilAST, primPos);
			break;
		case Token.FLOATLITERAL:
			FloatLiteral flAST = parseFloatLiteral();
			finish(primPos);
			exprAST = new FloatExpr(flAST, primPos);
			break;
		case Token.BOOLEANLITERAL:
			BooleanLiteral blAST = parseBooleanLiteral();
			finish(primPos);
			exprAST = new BooleanExpr(blAST, primPos);
			break;
		case Token.STRINGLITERAL:
			StringLiteral slAST = parseStringLiteral();
			finish(primPos);
			exprAST = new StringExpr(slAST, primPos);
			break;
		default:
			syntacticError("illegal primary expression", currentToken.spelling);

		}
		return exprAST;
	}

	// ========================== ID, OPERATOR and LITERALS
	// ========================

	Ident parseIdent() throws SyntaxError {

		Ident I = null;

		if (currentToken.kind == Token.ID) {
			previousTokenPosition = currentToken.position;
			String spelling = currentToken.spelling;
			I = new Ident(spelling, previousTokenPosition);
			currentToken = scanner.getToken();
		} else
			syntacticError("identifier expected here", "");
		return I;
	}

	// acceptOperator parses an operator, and constructs a leaf AST for it

	Operator acceptOperator() throws SyntaxError {
		Operator O = null;

		previousTokenPosition = currentToken.position;
		String spelling = currentToken.spelling;
		O = new Operator(spelling, previousTokenPosition);
		currentToken = scanner.getToken();
		return O;
	}

	// literals
	IntLiteral parseIntLiteral() throws SyntaxError {
		IntLiteral IL = null;

		if (currentToken.kind == Token.INTLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			IL = new IntLiteral(spelling, previousTokenPosition);
		} else
			syntacticError("integer literal expected here", "");
		return IL;
	}

	FloatLiteral parseFloatLiteral() throws SyntaxError {
		FloatLiteral FL = null;

		if (currentToken.kind == Token.FLOATLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			FL = new FloatLiteral(spelling, previousTokenPosition);
		} else
			syntacticError("float literal expected here", "");
		return FL;
	}

	BooleanLiteral parseBooleanLiteral() throws SyntaxError {
		BooleanLiteral BL = null;

		if (currentToken.kind == Token.BOOLEANLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			BL = new BooleanLiteral(spelling, previousTokenPosition);
		} else
			syntacticError("boolean literal expected here", "");
		return BL;
	}

	StringLiteral parseStringLiteral() throws SyntaxError {
		StringLiteral SL = null;
		if (currentToken.kind == Token.STRINGLITERAL) {
			String spelling = currentToken.spelling;
			// currentToken = scanner.getToken();
			accept();
			SL = new StringLiteral(spelling, previousTokenPosition);
		} else {
			syntacticError("boolean literal expected here", "");
		}
		return SL;
	}

}
