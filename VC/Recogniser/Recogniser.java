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
			// parseFuncDecl();
			parseExpr();
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" wrong result type for a function",
						currentToken.spelling);
			}
		} catch (SyntaxError s) {
		}
	}

	// ========================== DECLARATIONS ========================

	void parseFuncDecl() throws SyntaxError {

		match(Token.VOID);
		parseIdent();
		match(Token.LPAREN);
		match(Token.RPAREN);
		parseCompoundStmt();
	}

	// ======================= STATEMENTS ==============================

	void parseCompoundStmt() throws SyntaxError {

		match(Token.LCURLY);
		parseStmtList();
		match(Token.RCURLY);
	}

	// Here, a new nontermial has been introduced to define { stmt } *
	void parseStmtList() throws SyntaxError {

		while (currentToken.kind != Token.RCURLY)
			parseStmt();
	}

	void parseStmt() throws SyntaxError {

		switch (currentToken.kind) {

		case Token.CONTINUE:
			parseContinueStmt();
			break;

		default:
			parseExprStmt();
			break;

		}
	}

	void parseContinueStmt() throws SyntaxError {

		match(Token.CONTINUE);
		match(Token.SEMICOLON);

	}

	void parseExprStmt() throws SyntaxError {

		if (currentToken.kind == Token.ID
				|| currentToken.kind == Token.INTLITERAL
				|| currentToken.kind == Token.MINUS
				|| currentToken.kind == Token.LPAREN) {
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
			System.out.println("id found");
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
		System.out.println("hello there");
		parseAssignExpr();
	}

	void parseAssignExpr() throws SyntaxError {
//		parseAdditiveExpr();
		parseCondOrExpr();
		switch(currentToken.kind){
		case Token.EQ:
			acceptOperator();
			parseCondOrExpr();
			break;
		default:
			break;
		}
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
			parseCondAndExpr();
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
			parseEqualityExpr();
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
//		while (currentToken.kind == Token.MULT) {
//			acceptOperator();
//			parseUnaryExpr();
//		}
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
		System.out.println("this: "  + currentToken.spelling);
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
			break;

		case Token.LPAREN: {
			accept();
			parseExpr();
			match(Token.RPAREN);
		}
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
		default:
			syntacticError("illegal parimary expression", currentToken.spelling);

		}
	}

	// ========================== LITERALS ========================

	// Call these methods rather than accept(). In Assignment 3,
	// literal AST nodes will be constructed inside these methods.

	void parseIntLiteral() throws SyntaxError {
		System.out.println("found intliteral");
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
}
