/***
 * *
 * * Recogniser.java            
 * *
 ***/

/* At this stage, this parser accepts a subset of VC defined	by
 * the following grammar. 
 *
 * You need to modify the supplied parsing methods (if necessary) and 
 * add the missing ones to obtain a parser for the VC language.
 *
 * (23-*-March-*-2014)

 program       -> func-decl

 // declaration

 func-decl     -> void identifier "(" ")" compound-stmt

 identifier    -> ID

 // statements 
 compound-stmt -> "{" stmt* "}" 
 stmt          -> continue-stmt
 |  expr-stmt
 continue-stmt -> continue ";"
 expr-stmt     -> expr? ";"

 // expressions 
 expr                -> assignment-expr
 assignment-expr     -> additive-expr
 additive-expr       -> multiplicative-expr
 |  additive-expr "+" multiplicative-expr
 multiplicative-expr -> unary-expr
 |  multiplicative-expr "*" unary-expr
 unary-expr          -> "-" unary-expr
 |  primary-expr

 primary-expr        -> identifier
 |  INTLITERAL
 | "(" expr ")"
 */

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

	private Scanner scanner;
	private ErrorReporter errorReporter;
	private Token currentToken;
	private boolean firstIdChecked;

	public Recogniser(Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		currentToken = scanner.getToken();
	}

	// match checks to see f the current token matches tokenExpected.
	// If so, fetches the next token.
	// If not, reports a syntactic error.

	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			currentToken = scanner.getToken();
		} else {
			syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
	}

	// accepts the current token and fetches the next
	void accept() {
		currentToken = scanner.getToken();
	}

	void syntacticError(String messageTemplate, String tokenQuoted)
			throws SyntaxError {
		SourcePosition pos = currentToken.position;
		errorReporter.reportError(messageTemplate, tokenQuoted, pos);
		throw (new SyntaxError());
	}

	// ========================== PROGRAMS ========================

	public void parseProgram() {
		
		try {
			//(func - decl | var - decl)*
			while(currentToken.kind != Token.EOF){
				parseProgramPrime();
			}
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" wrong result type for a function",
						currentToken.spelling);
			}
		} catch (SyntaxError s) {
		}
	}

	// ========================== DECLARATIONS ========================

	void parseProgramPrime() throws SyntaxError{
		firstIdChecked = false;
		// parse type
		parseType();
		parseIdent();
	
		// check if id
		
		if(currentToken.kind == Token.LPAREN){
			parseFuncDeclPrime();
		}else{
			//else parse var
			firstIdChecked = true;
			parseVarDeclPrime();

		}
		 
		
		
		
	}
	
	void parseFuncDecl() throws SyntaxError {
		parseType();
		parseIdent();
		parseParaList();
		parseCompoundStmt();
	}
	void parseFuncDeclPrime() throws SyntaxError {
//		parseIdent();
		parseParaList();
		parseCompoundStmt();
	}

	void parseVarDecl() throws SyntaxError {
		parseType();
		parseInitDeclaratorList();
		match(Token.SEMICOLON);
	}
	
	void parseVarDeclPrime() throws SyntaxError{
		parseInitDeclaratorList();
		match(Token.SEMICOLON);
	}
	
	void parseInitDeclaratorList() throws SyntaxError{
		parseInitDeclarator();
		parseInitDeclaratorListStar();
	}
	
	void parseInitDeclaratorListStar() throws SyntaxError{
		//nullible
		switch(currentToken.kind){
		case Token.COMMA:
			accept();
			parseInitDeclarator();
			parseInitDeclaratorListStar();
			break;
		default:
			
			break;
		}
		
	}
	
	void parseInitDeclarator() throws SyntaxError{
		parseDeclarator();
		parseInitDeclaratorQuestion();
	}
	
	void parseInitDeclaratorQuestion() throws SyntaxError{
		switch(currentToken.kind){
		case Token.EQ:
			accept();
			parseInitialiser();
			break;
		default:
			break;
		}
	}
	
	void parseDeclarator() throws SyntaxError{
		if(firstIdChecked){
			firstIdChecked = false;
		}else{
			parseIdent();
		}
	
		parseDeclaratorQuestion();
	}
	
	void parseDeclaratorQuestion() throws SyntaxError{
		switch(currentToken.kind){
		case Token.LBRACKET:
			match(Token.LBRACKET);
			if(currentToken.kind == Token.INTLITERAL){
				parseIntLiteral();
			}
			match(Token.RBRACKET);
			break;
		default:
			break;
		}
	}
	
	void parseInitialiser() throws SyntaxError{
		switch(currentToken.kind){
		case Token.LCURLY:
			accept();
			parseExpr();
			parseInitStar();
			match(Token.RCURLY);
			break;
		default:
			parseExpr();
			break;
		}
	}
	
	void parseInitStar() throws SyntaxError{
		switch(currentToken.kind){
		case Token.COMMA:
			accept();
			parseExpr();
			parseInitStar();
			break;
		default:
			break;
		}
	}
	
	// primitive typess
	
	void parseType() throws SyntaxError{
		// type is not nullible
		switch(currentToken.kind){
		case Token.VOID:
		case Token.BOOLEAN:
		case Token.INT:
		case Token.FLOAT:
			accept();
			break;
		default:
			syntacticError("Expecting a Type declarator",
					currentToken.spelling);
			break;
		}
	}
	
	// ======================= STATEMENTS ==============================

	void parseCompoundStmt() throws SyntaxError {
		boolean inStatementPhase = false;
		match(Token.LCURLY);
		while(currentToken.kind != Token.RCURLY){
			//if the first thing is not type
			if(checkType() && !inStatementPhase){
				
				parseVarDecl();
			}else{
				parseStmt();
				inStatementPhase = true;
			}
		}
		match(Token.RCURLY);
	}

	boolean checkType(){
		boolean isAType = true;
		if(currentToken.kind != Token.VOID && currentToken.kind != Token.BOOLEAN && currentToken.kind != Token.INT && currentToken.kind != Token.FLOAT){
			isAType = false;
		}
		return isAType;
	}
	
	// Here, a new nontermial has been introduced to define { stmt } *
	void parseStmtList() throws SyntaxError {

		while (currentToken.kind != Token.RCURLY)
			parseStmt();
	}

	void parseStmt() throws SyntaxError {

		switch (currentToken.kind) {
		case Token.LCURLY:
			parseCompoundStmt();
			break;
		case Token.IF:
			parseIfStmt();
			break;
		case Token.FOR:
			parseForStmt();
			break;
		case Token.WHILE:
			parseWhileStmt();
			break;
		case Token.BREAK:
			parseBreakStmt();
			break;
		case Token.CONTINUE:
			parseContinueStmt();
			break;
		case Token.RETURN:
			parseReturnStmt();
			break;		
		default:
			parseExprStmt();
			break;

		}
	}
	
	void parseIfStmt() throws SyntaxError{
		match(Token.IF);
		match(Token.LPAREN);
		parseExpr();
		match(Token.RPAREN);
		parseStmt();
		// optional else 
		if(currentToken.kind == Token.ELSE){
			match(Token.ELSE);
			parseStmt();
		}
	}
	
	void parseForStmt() throws SyntaxError{
		match(Token.FOR);
		match(Token.LPAREN);
		//  expr? <-- to do
		if(currentToken.kind != Token.SEMICOLON){
			parseExpr();
		}
		match(Token.SEMICOLON);
		// expr ? <-- to do
		if(currentToken.kind != Token.SEMICOLON){
			parseExpr();
		}
		match(Token.SEMICOLON);
		if(currentToken.kind != Token.RPAREN){
			parseExpr();
		}
		match(Token.RPAREN);
		parseStmt();
	}
	
	void parseWhileStmt() throws SyntaxError{
		match(Token.WHILE);
		match(Token.LPAREN);
		parseExpr();
		match(Token.RPAREN);
		parseStmt();
	}
	
	void parseBreakStmt() throws SyntaxError{
		match(Token.BREAK);
		match(Token.SEMICOLON);
	}
	
	void parseReturnStmt() throws SyntaxError{
		match(Token.RETURN);
		if(currentToken.kind != Token.SEMICOLON){
			parseExpr();
		}
		match(Token.SEMICOLON);
	}

	void parseContinueStmt() throws SyntaxError {

		match(Token.CONTINUE);
		match(Token.SEMICOLON);

	}

	void parseExprStmt() throws SyntaxError {

		if (currentToken.kind == Token.ID
				|| currentToken.kind == Token.INTLITERAL
				|| currentToken.kind == Token.MINUS
				|| currentToken.kind == Token.LPAREN
				|| currentToken.kind == Token.BOOLEANLITERAL
				|| currentToken.kind == Token.STRINGLITERAL
				|| currentToken.kind == Token.FLOATLITERAL
				|| currentToken.kind == Token.PLUS
				|| currentToken.kind == Token.NOT
				) {
			parseExpr();
			match(Token.SEMICOLON);
		} else {
			match(Token.SEMICOLON);
		}
	}

	// ======================= IDENTIFIERS ======================

	// Call parseIdent rather than match(Token.ID).
	// In Assignment 3, an Identifier node will be constructed in here.

	void parseIdent() throws SyntaxError {

		if (currentToken.kind == Token.ID) {
			currentToken = scanner.getToken();
		} else
			syntacticError("identifier expected here", "");
	}

	// ======================= OPERATORS ======================

	// Call acceptOperator rather than accept().
	// In Assignment 3, an Operator Node will be constructed in here.

	void acceptOperator() throws SyntaxError {

		currentToken = scanner.getToken();
	}

	// ======================= EXPRESSIONS ======================

	void parseExpr() throws SyntaxError {
		parseAssignExpr();
	}

	void parseAssignExpr() throws SyntaxError {
		parseCondOrExpr();
		parseAssignExprStar();
	}
	
	void parseAssignExprStar() throws SyntaxError{
		//first is a equals
		switch(currentToken.kind){
		case Token.EQ:
			acceptOperator();
			parseCondOrExpr();
			parseAssignExprStar();
			break;
		default:
			break;
		}
		// can be nullible
	}

	void parseCondOrExpr() throws SyntaxError {
		parseCondAndExpr();
		parseCondOrExprPrime();
	}

	void parseCondOrExprPrime() throws SyntaxError{
		switch(currentToken.kind){
		case Token.OROR:
			acceptOperator();
			parseCondAndExpr();
			parseCondOrExprPrime();
			break;
		default:
			// nullible
			break;
		}
	}
	
	void parseCondAndExpr() throws SyntaxError {
		parseEqualityExpr();
		parseCondAndExprPrime();
	}
	void parseCondAndExprPrime() throws SyntaxError {
		switch(currentToken.kind){
		case Token.ANDAND:
			acceptOperator();
			parseEqualityExpr();
			parseCondAndExprPrime();
			break;
		default:
			// nullible
			break;
		}
	}
	
	void parseEqualityExpr() throws SyntaxError {
		parseRelExpr();
		parseEqualityExprPrime();
	}
	
	void parseEqualityExprPrime() throws SyntaxError {
		switch(currentToken.kind){
		case Token.EQEQ:
		case Token.NOTEQ:
			acceptOperator();
			parseRelExpr();
			parseEqualityExprPrime();
			default:
				//nullible
				break;
		}
	}
	
	void parseRelExpr() throws SyntaxError {
		// parse additive exp
		parseAdditiveExpr();
		//parse additive prime
		parseRelExprPrime();
	}
	
	void parseRelExprPrime() throws SyntaxError{
		// check for the gt|e and lt|e operators
		switch(currentToken.kind){
		case Token.GT:
		case Token.GTEQ:
		case Token.LT:
		case Token.LTEQ:
			acceptOperator();
			parseAdditiveExpr();
			parseRelExprPrime();
			break;
		default:
			//nullible
			break;
		}
	}

	void parseAdditiveExpr() throws SyntaxError {

		parseMultiplicativeExpr();
		parseAdditiveExprPrime();
	}

	void parseAdditiveExprPrime() throws SyntaxError{
		switch(currentToken.kind){
		case Token.PLUS:
			acceptOperator();
			parseMultiplicativeExpr();
			parseAdditiveExprPrime();
			break;
		case Token.MINUS:
			acceptOperator();
			parseMultiplicativeExpr();
			parseAdditiveExprPrime();
			break;
		default:
			// this is the nullible state
			break;
		}
	}
	void parseMultiplicativeExpr() throws SyntaxError {
		parseUnaryExpr();
		parseMultiplicativeExprPrime();
	}
	
	void parseMultiplicativeExprPrime() throws SyntaxError {
		
		switch(currentToken.kind){
		// multiply ue
		case Token.MULT:
			acceptOperator();
			parseUnaryExpr();
			parseMultiplicativeExprPrime();
			break;
		// div ue
		case Token.DIV: 
			acceptOperator();
			parseUnaryExpr();
			parseMultiplicativeExprPrime();
			break;
		//  empty // figure out how to code empty
		default:
			// empty / nullible?
			break;
		}
		
		
	}

	void parseUnaryExpr() throws SyntaxError {
		switch (currentToken.kind) {
		case Token.MINUS: 
		case Token.PLUS:
		case Token.NOT:
			acceptOperator();
			parseUnaryExpr();
			break;
		default:
			parsePrimaryExpr();
			break;
		}
	}

	void parsePrimaryExpr() throws SyntaxError {
		
		switch (currentToken.kind) {
		case Token.ID:
			parseIdent();
			if(currentToken.kind == Token.LPAREN){
				parseArgList();
			}else if(currentToken.kind == Token.LBRACKET){
				match(Token.LBRACKET);
				parseExpr();
				match(Token.RBRACKET);
			}
			break;
		case Token.LPAREN: 
			accept();
			parseExpr();
			match(Token.RPAREN);
			break;
		case Token.INTLITERAL:
			parseIntLiteral();
			break;
		case Token.FLOAT:
			parseFloatLiteral();
			break;
		case Token.BOOLEANLITERAL:
			parseBooleanLiteral();
			break;
		case Token.STRINGLITERAL:
			parseStringLiteral();
			break;
		default:
			syntacticError("illegal parimary expression", currentToken.spelling);

		}
	}

	// ========================== LITERALS ========================

	// Call these methods rather than accept(). In Assignment 3,
	// literal AST nodes will be constructed inside these methods.

	void parseIntLiteral() throws SyntaxError {
		if (currentToken.kind == Token.INTLITERAL) {
			currentToken = scanner.getToken();
		} else
			syntacticError("integer literal expected here", "");
	}

	void parseFloatLiteral() throws SyntaxError {

		if (currentToken.kind == Token.FLOATLITERAL) {
			currentToken = scanner.getToken();
		} else
			syntacticError("float literal expected here", "");
	}

	void parseBooleanLiteral() throws SyntaxError {

		if (currentToken.kind == Token.BOOLEANLITERAL) {
			currentToken = scanner.getToken();
		} else
			syntacticError("boolean literal expected here", "");
	}
	void parseStringLiteral() throws SyntaxError{

		if (currentToken.kind == Token.STRINGLITERAL) {
			currentToken = scanner.getToken();
		} else
			syntacticError("boolean literal expected here", "");
	}
	//======================== PARAMS ==========================
	void parseParaList() throws SyntaxError{
		match(Token.LPAREN);
		if(currentToken.kind != Token.RPAREN){
			parseProperParaList();
		}
		match(Token.RPAREN);
	}
	
	void parseProperParaList() throws SyntaxError{
		parseParaDecl();
		parseProperParaListStar();
	}
	
	void parseProperParaListStar() throws SyntaxError{
		switch(currentToken.kind){
		case Token.COMMA:
			accept();
			parseParaDecl();
			parseProperParaListStar();
			break;
		default:
			break;
		}
	}
	
	void parseParaDecl() throws SyntaxError{
		parseType();
		parseDeclarator();
	}
	
	void parseArgList() throws SyntaxError{
		match(Token.LPAREN);
		if(currentToken.kind != Token.RPAREN){
			parseProperArgList();
		}
		match(Token.RPAREN);
	}
	
	void parseProperArgList() throws SyntaxError{
		parseArg();
		parseProperArgListStar();
	}
	
	void parseProperArgListStar() throws SyntaxError{
		switch(currentToken.kind){
		case Token.COMMA:
			accept();
			parseArg();
			break;
		default:
			break;
		}
	}
	
	void parseArg() throws SyntaxError{
		parseExpr();
	}
	//latest code as of thursday 9th 2304 hours
}
