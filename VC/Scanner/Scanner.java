/**
 **	Scanner.java                        
 **/

package VC.Scanner;

import VC.ErrorReporter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;



public final class Scanner {

	private SourceFile sourceFile;
	private boolean debug;
	private static final int TAB_SPACE = 8;
	private static char[] TRUE_ARRAY  = {'t','r','u','e'};
	private static char[] FALSE_ARRAY = {'f','a','l','s','e'};
	private ErrorReporter errorReporter;
	private StringBuffer currentSpelling;
	private char currentChar;
	private SourcePosition sourcePos;
	private TextCount counter;
	private int lineCount;
	private int columnCount;
	private Map<WordState, Integer> stateMap =  new HashMap<WordState, Integer>();
	public enum WordState {
		noChar, integerState, floatState, exponentState, keyword, variable, error, dotState, eState , end, opState, illegal
	}
	// =========================================================

	public Scanner(SourceFile source, ErrorReporter reporter) {
		sourceFile = source;
		errorReporter = reporter;
		currentChar = sourceFile.getNextChar();
		debug = false;
		counter = new TextCount();
		lineCount = 1;
		columnCount = 1;
	}

	public void enableDebugging() {
		debug = true;
	}

	// accept gets the next character from the source program.

	
	private void accept() {
		if(currentChar == '\r' || currentChar == '\n'){
			counter.incrementLineCounter();
			counter.resetColumn();
		}else{
			counter.incrementColumnCounter();
		}
		currentChar = sourceFile.getNextChar();

		// you may save the lexeme of the current token incrementally here
		// you may also increment your line and column counters here
	}

	private void specialAccept(){
		counter.incrementColumnCounter();
		currentChar = sourceFile.getNextChar();
	}
	
	// inspectChar returns the n-th character after currentChar
	// in the input stream.
	//
	// If there are fewer than nthChar characters between currentChar
	// and the end of file marker, SourceFile.eof is returned.
	//
	// Both currentChar and the current position in the input stream
	// are *not* changed. Therefore, a subsequent call to accept()
	// will always return the next char after currentChar.

	private char inspectChar(int nthChar) {
		return sourceFile.inspectChar(nthChar);
	}

	private int nextToken() {
		// Tokens: separators, operators, literals, identifiers and keyworods
		switch (currentChar) {
		// separators
		case '(':
			addCharToString();
			accept();
			return Token.LPAREN;
		case '|':
			addCharToString();
			accept();
			if (currentChar == '|') {
				addCharToString();
				accept();
				return Token.OROR;
			} else {
				return Token.ERROR;
			}
		case '[':
			addCharToString();
			accept();
			return Token.LBRACKET;
		case ']':
			addCharToString();
			accept();
			return Token.RBRACKET;
			
		case ')':
			addCharToString();
			accept();
			return Token.RPAREN;
		case '}':
			addCharToString();
			accept();
			return Token.RCURLY;
		case '{':
			addCharToString();
			accept();
			return Token.LCURLY;
		case '*':
			addCharToString();
			accept();
			return Token.MULT;
		case '+':
			addCharToString();
			accept();
			return Token.PLUS;
		case '&':
			addCharToString();
			accept();
			if(currentChar == '&'){
				addCharToString();
				accept();
				return Token.ANDAND;
			}
			return Token.ERROR;
		case '-':
			addCharToString();
			accept();
			return Token.MINUS;
		case ';':
			addCharToString();
			accept();
			return Token.SEMICOLON;
		case SourceFile.eof:
			currentSpelling.append(Token.spell(Token.EOF));
			accept();
			//			counter.incrementColumnCounter();
			return Token.EOF;
		case ',':
			addCharToString();
			accept();
			return Token.COMMA;
		case '/':
			addCharToString();
			accept();
			return Token.DIV;
		case '<':
			addCharToString();
			accept();
			if(currentChar == '='){
				addCharToString();
				accept();
				return Token.LTEQ;
			}
			return Token.LT;
		case '>' :
			addCharToString();
			accept();
			if(currentChar == '='){
				addCharToString();
				accept();
				return Token.GTEQ;
			}
			return Token.GT;
		case '=':
			addCharToString();
			accept();
			if(currentChar == '='){
				addCharToString();
				accept();
				return Token.EQEQ;	
			}
			return Token.EQ;
		case '"':
			accept();
			//this should be the start of the token ? 
//			sourcePos.charStart = counter.columnCount;
			checkString();
			return Token.STRINGLITERAL;
		case '!':
			addCharToString();
			accept();
			if(currentChar == '='){
				addCharToString();
				accept();
				return Token.NOTEQ;
			}
			return Token.NOT;
		default:
			int token = -1;
			if(isLegal()){
				token = checkForLiteral();
			}else{
//				 must be illegal? 
				addCharToString();
				accept();
				token = Token.ERROR;
			}
			//			accept();
			return token;
		}
	}

	private void addCharToString(){
		currentSpelling.append(currentChar);
	}

	void skipSpaceAndComments() {
		// needs to find the start of the next token
		boolean skippableFound = true;
		while(skippableFound){
			switch (currentChar) {
			case ' ':
				// check for space
				skippableFound = ignoreWhiteSpaces();
				break;
				// check for slash
			case '/':
				// inspect the next char
				skippableFound = checkForCommentChars();
				break;
			case '\n':
				skippableFound = true;
				accept();
				break;
			case '\r':
				skippableFound = true;
				accept();
				break;
			case '\t':
				skippableFound = true;
				// csomplete me
				checkSpacesToSkip();
				accept();
			default:
				skippableFound = false;
				break;
			}
		}
	}
	//========================++TOKEN==============================================================
	public Token getToken() {
		Token tok;
		int kind;

		// skip white space and comments
		skipSpaceAndComments();

		currentSpelling = new StringBuffer("");

		sourcePos = new SourcePosition();
		sourcePos.lineStart = counter.getLineCount();
		sourcePos.charStart = counter.getColumnCount();
		// You must record the position of the current token somehow

		kind = nextToken();

		sourcePos.lineFinish = counter.getLineCount();
		sourcePos.charFinish = counter.getColumnCount() - 1;
		tok = new Token(kind, currentSpelling.toString(), sourcePos);

		// * do not remove these three lines
		if (debug)
			System.out.println(tok);
		return tok;
	}

	private boolean checkForCommentChars() {
		// check that the next char is etiher a star or a slash
		boolean commentFound = false;
		boolean isStar = false;
		boolean endOfComment = false;

		if (sourceFile.inspectChar(1) == '/') {
			commentFound = true;
		} else if (sourceFile.inspectChar(1) == '*') {
			commentFound = true;
			isStar = true;
		}
		if(commentFound){
			acceptMultiple(2);
		}
		if (commentFound) {
			// skip eveyrthing until either EOF or * then /
			while(currentChar != SourceFile.eof && !checkForCommentEnd(isStar)){
				// check to make sure that it is not end of comment
				accept();
			}
		}
		return commentFound;
	}

	private boolean ignoreWhiteSpaces() {
		boolean whiteSpaces = false;
		while (currentChar == ' ') {
			// check the current char is white
			accept();
			whiteSpaces = true;
		}
		return whiteSpaces;
	}

	private boolean checkForCommentEnd(boolean isStar) {
		char nextChar = ' ';
		boolean endCommentFound = false;
		if(isStar){
			nextChar = sourceFile.inspectChar(1);
			if (currentChar == '*' && nextChar == '/') {
				endCommentFound = true;
				acceptMultiple(2);
			}else if(currentChar == SourceFile.eof || nextChar == SourceFile.eof){
				System.out.println("ERROR: UNTERMINATED COMMENT");

			}
		}else{
			if(currentChar == '\n' || currentChar == '\r'){
				endCommentFound = true;
				accept();
			}
		}
		return endCommentFound;
	}
	private void acceptMultiple(int n){
		int count = 0;
		while(count < n){
			accept();
			count++;
		}
	}
	private int checkForLiteral(){
		int token = 0;
		WordState curWordState = WordState.noChar;
		LinkedList<WordState> stateList = new LinkedList<WordState>();
		int charDelta = 0;
		StringBuffer strBuff = new StringBuffer();
		char c = ' ';
		while(curWordState != WordState.error ){
			// from cur char onwards
			c = charDelta > 0 ?  sourceFile.inspectChar(charDelta) : currentChar;
			if(checkIsAlpha(c)){
				if(curWordState == WordState.noChar || curWordState == WordState.variable){
					// letters can only be part of word
					curWordState = WordState.variable;
				}else if((c == 'e' || c == 'E') && (curWordState == WordState.floatState || curWordState == WordState.integerState || curWordState == WordState.dotState) ){
					curWordState = WordState.eState;
				}else {
					curWordState = WordState.error;
				}
			}else if(checkIsDig(c)){
				if(curWordState == WordState.noChar){
					// if no char then it is the start of a number 
					curWordState = WordState.integerState;
				}else if(curWordState == WordState.dotState || curWordState == WordState.floatState || curWordState == WordState.eState || curWordState == WordState.opState){
					curWordState = WordState.floatState;
				}else if(curWordState == WordState.integerState){
					curWordState = WordState.integerState;
				}else if(curWordState == WordState.variable){
					curWordState = WordState.variable;
				}else{
					curWordState = WordState.error;
				}

			}else if(c == '.'){
				if(curWordState == WordState.integerState || curWordState == WordState.noChar){
					curWordState = WordState.dotState;
				}else { 
					curWordState = WordState.error;
				}
			}else if(c == '+' || c == '-'){
				if(curWordState == WordState.eState){
					curWordState = WordState.opState;
				}else{
					curWordState = WordState.error;
				}
			}else{
				curWordState = WordState.error;
			}
			strBuff.append(c);
			charDelta++;
			stateList.addFirst(curWordState);
		}

		boolean goodToken = false;
		//convert the strBuff to char arry
		//after error we  know that the last character was wrong . accept up to that point and return. 
		strBuff.deleteCharAt(strBuff.length() - 1);
		stateList.pop();

		while(!goodToken && strBuff.length() > 0){
			// while there is not an accepted token || char is not empy
			WordState listState = stateList.getFirst();
			// check the char[] against what token is on the statelist
			//convert the string into a char arary
			char[] array = strBuff.toString().toCharArray();
			if(listState == WordState.variable){
				//check if its true or false
				if(array.length == 4 || array.length == 5){
					goodToken = checkBoolLiteral(array);
					token = Token.BOOLEANLITERAL;
				}
				if (!goodToken){
					//				 else check if its id
					goodToken = checkId(array);
					token = Token.ID;
				}

			}else if(listState == WordState.floatState || listState == WordState.dotState){
				if(checkOnlyDot(array)){
					goodToken = true;
					token = Token.ERROR;
				}else{
					goodToken = checkFloat(array);
					token = Token.FLOATLITERAL;
				}
				

			} else if(listState == WordState.integerState) {
				goodToken = checkIsInt(array);
				token = Token.INTLITERAL;
			}
			// still need to check for true false
			if(!goodToken){
				strBuff.deleteCharAt(strBuff.length() - 1);
				stateList.pop();
			}
			// accept char[].length e and add char.length to string buffer 
		}
		// we need to accept and append strBuffer length chars
		appendToStrBuffer(strBuff.length());
		
		return token;
	}
	boolean checkOnlyDot(char[] array ){
		boolean isDot = false;
		if(array.length == 1 && array[0] == '.'){
			isDot = true;
		}
		return isDot;
	}
	boolean checkId(char[] array){
		boolean isId = true;
		// _|[A-Z]|[a-z]([a-zA-Z0--9_])*
		if(array[0] >= '0' && array[0] <= '9'){
			isId = false;
		}else{
			char c = ' ';
			//check the rest of the string only consists of letters numbers and underscores
			for(int i = 0; i < array.length; i++){
				c = array[i];
				if(!checkIsAlpha(c) && !checkIsDig(c) && c != '_'){
					isId = false;
				}
			}
		}
		return isId;
	}
	boolean checkFloat(char[] floatLiteral){
		boolean isGood = false;
		int eCount = 0;
		int opCount = 0;
		char lastLetter = floatLiteral[floatLiteral.length-1];
		for(int i = 0; i < floatLiteral.length; i++){
			char curC = floatLiteral[i];
			if(curC == 'e' || curC == 'E'){
				eCount++;
			}else if(curC == '+' || curC == '-'){
				opCount++;
			}
		}
		if(eCount <= 1 && opCount <=1){
			isGood = true;
		}else {
			isGood = false;
		}

		if(lastLetter == 'e' || lastLetter == 'E' || lastLetter == '+' || lastLetter == '-'){
			isGood = false;
		}
		
		return isGood;
	}

	boolean checkIsInt(char[] array){
		boolean isInt = true;
		for(int i = 0; i < array.length; i++){
			if(!(array[i] >= '0' && array[i] <= '9')){
				isInt = false;
			}
		}
		return isInt;
	}

	boolean checkBoolLiteral(char[] array){
		boolean isBoolLiteral = false;
		//check if spelling is true  or false
		if(Arrays.equals(array,TRUE_ARRAY) || Arrays.equals(array, FALSE_ARRAY)){
			isBoolLiteral = true;
		}

		return isBoolLiteral;
	}

	boolean checkIsAlpha(char c){
		boolean isAlpha = false;
		if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')){
			isAlpha = true;
		}
		return isAlpha;
	}

	boolean checkIsDig(char c){
		boolean isDig = false;
		if( c >= '0' && c <= '9'){
			isDig = true;
		}
		return isDig;
	}
	void appendToStrBuffer(int x){
		for(int i = 0; i < x; i++){
			addCharToString();
			accept();
		}
	}

	void appendToStrBufferWithCheck(int x){
		for(int i = 0; i < x; i++){
			char nextChar = inspectChar(1);
			if(currentChar == '\\' && checkEscapeChar(nextChar)){
				currentChar = getEscape(nextChar);
				addCharToString();
				specialAccept();
				specialAccept();
				i = i + 1;
			
			}else{
				addCharToString();
				accept();
			}
		}
	}
	
	
	void checkString(){
		// we've seen and accepted the first quotatinon marks
		//while " is not encountered
		StringBuffer buffer = new StringBuffer();
		String errorString = "";
		int charDelta = 0;
		boolean error = false;
		boolean escaped = false;
		char prevChar = ' ';
		char c = ' ';
		while(c != SourceFile.eof && (c != '"' ) && !error){
			c = charDelta == 0 ? currentChar : sourceFile.inspectChar(charDelta);
			buffer.append(c);
			if(c == '\n' || c == SourceFile.eof){
				errorString = "ERROR: untermintated string\n";
				error = true;
//				charDelta;
			}
			if(c == '\\'){
				char nextChar = sourceFile.inspectChar(charDelta+1);
				boolean isValid = checkEscapeChar(nextChar);
				if(isValid){
					charDelta = charDelta + 1;
				}
				if(!isValid){
					errorString = "ERROR: illegal escape char\n";
//					error = true;
				}
//				charDelta++;
			}
			charDelta++;
		}
		appendToStrBufferWithCheck(charDelta-1);
		// get rid of the trailing quotes
		System.out.print(errorString);
		if(currentChar == '"'){
			accept();
		}

	}
	
	

	boolean isLegal(){
		boolean isLegal = false;
		char c = currentChar;
		if(checkIsDig(c) || c == ' ' || checkIsAlpha(c) || c == '.' || c == '+' || c == '-'){
			isLegal = true;
		}
		return isLegal;
	}
	
	boolean checkEscapeChar(char c){
		boolean isEscape = false;
		switch(c){
		case 'b':
		case 'f':
		case 'n':
		case 'r':
		case 't':
		case '\'':
		case '"':
		case '\\':
			isEscape = true;
			break;
		default:
			break;
		}
		return isEscape;
	}
	char getEscape(char c){
		char escapeChar = ' ';
	
		switch(c){
		case 'b':
			escapeChar = '\b';
			break;
		case 'f':
			escapeChar = '\f';
			break;
		case 'n':
			escapeChar = '\n';
			break;
		case 'r':
			escapeChar = '\r';
			break;
		case 't':
			escapeChar = '\t';
			break;
		case '\'':
			escapeChar = '\'';
			break;
		case '"':
			escapeChar = '\"';
			break;
		case '\\':
			escapeChar = '\\';
			break;
		}
		return escapeChar;
	}
	
	private void checkSpacesToSkip(){
		//get the current line count
		int curCol = counter.getColumnCount();
		// mod with 8? 
		int diff = 0;
		int delta = 0;
		int modulo = -1;
		// from cur col till modulo 8 = 0;
		while(modulo != 0){
			modulo = (curCol + delta) %8;
			delta++;
		}
//		int diff = curCol%8;
		// result is how many chars we should advance
		diff = delta == 0 ? 8 : delta;
		counter.incrementColumnCounterByN(diff-1);
	}
	//helper class for linecCounting
	private class TextCount {


		private int lineCount;
		private int columnCount;

		public TextCount(){
			lineCount = 1;
			columnCount = 1;
		}
		//setters
		public void incrementLineCounter(){
			lineCount++;
		}
		
		public void incrementColumnCounter(){
			columnCount++;
		}
		
		public void incrementLineCounterByN(int n){
			lineCount += n;
		}
		
		public void incrementColumnCounterByN(int n){
			columnCount += n;
		}
		
		public void resetColumn(){
			columnCount = 1;
		}
		
		public int getLineCount(){ return lineCount; }
		public int getColumnCount() { return columnCount; }
		
	}
	
	
}
