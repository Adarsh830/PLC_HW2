package com.arithmetic.lang;

import com.arithmetic.lang.AST.*;
import com.arithmetic.lang.Exception.ParserException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.arithmetic.lang.TokenType.*;

public class Parser {
	private List<Token> tokens;
	private int         pos = 0;

	public Parser(List<Token> tokens) {
		this.tokens = tokens;
		this.tokens.removeIf(
				token -> token.type == TokenType.SPACE || token.type == TokenType.LINE || token.type == COMMENT
						|| token.type == MULTI_COMMENT);

	}

	private void error(String message) {
		if (pos < tokens.size()) {
			Token t = tokens.get(pos);
			throw new ParserException(message + "\n line:  " + t.line);
		} else {
			throw new ParserException(message);
		}
	}

	private Token match(TokenType... expected) {
		if (pos < tokens.size()) {
			Token curr = tokens.get(pos);
			if (Arrays.asList(expected)
				.contains(curr.type)) { pos++; return curr; }
		}
		return null;
	}

	private Token require(TokenType... expected) {
		Token t = match(expected);
		if (t == null)
			error("Expected " + Arrays.toString(expected));
		return t;
	}

	private ExprNode parseElem() {
		Token num = match(TokenType.NUMBER);
		if (num != null)
			return new NumberNode(num);
		Token id = match(TokenType.ID);
		if (id != null)
			return new VarNode(id);
		error("Expected a number or id");
		return null;
	}

	private ExprNode parseParens() {
		if (match(TokenType.LPAR) != null) {
			ExprNode e = parseExpression();
			require(TokenType.RPAR);
			return e;
		} else {
			return parseElem();
		}
	}

	private ExprNode parseUnary() {
		Token t;
		ArrayDeque<Token> unaryDeque = new ArrayDeque<>();
		while ((t = match(NOT, SUB)) != null)
			unaryDeque.push(t);
		ExprNode e1 = parseParens();
		Token unaryPostIncDec = match(INC, DEC);
		if (unaryPostIncDec != null)
			e1 = new UnaryOpNode(unaryPostIncDec, e1);
		while (!unaryDeque.isEmpty())
			e1 = new UnaryOpNode(unaryDeque.pop(), e1);
		return e1;
	}

	private ExprNode parseDivMul() {
		ExprNode e1 = parseUnary();
		Token op;
		while ((op = match(TokenType.MUL, TokenType.DIV)) != null) {
			ExprNode e2 = parseUnary();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private ExprNode parseAddSub() {
		ExprNode e1 = parseDivMul();
		Token op;
		while ((op = match(TokenType.ADD, TokenType.SUB)) != null) {
			ExprNode e2 = parseDivMul();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private ExprNode parseLessLEqualGreaterGEqual() {
		ExprNode e1 = parseAddSub();
		Token op;
		while ((op = match(LESS, LESS_EQUAL, GREATER, GREATER_EQUAL)) != null) {
			ExprNode e2 = parseAddSub();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private ExprNode parseEqualNEqual() {
		ExprNode e1 = parseLessLEqualGreaterGEqual();
		Token op;
		while ((op = match(EQUAL, NEQUAL)) != null) {
			ExprNode e2 = parseLessLEqualGreaterGEqual();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private ExprNode parseAnd() {
		ExprNode e1 = parseEqualNEqual();
		Token op;
		while ((op = match(TokenType.AND)) != null) {
			ExprNode e2 = parseEqualNEqual();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private ExprNode parseXor() {
		ExprNode e1 = parseAnd();
		Token op;
		while ((op = match(XOR)) != null) { ExprNode e2 = parseAnd(); e1 = new BinOpNode(op, e1, e2); }
		return e1;
	}

	private ExprNode parseOr() {
		ExprNode e1 = parseXor();
		Token op;
		while ((op = match(OR)) != null) { ExprNode e2 = parseXor(); e1 = new BinOpNode(op, e1, e2); }
		return e1;
	}

	private ExprNode parseLogicalAnd() {
		ExprNode e1 = parseOr();
		Token op;
		while ((op = match(LAND)) != null) { ExprNode e2 = parseOr(); e1 = new BinOpNode(op, e1, e2); }
		return e1;
	}

	private ExprNode parseLogicalOr() {
		ExprNode e1 = parseLogicalAnd();
		Token op;
		while ((op = match(LOR)) != null) { ExprNode e2 = parseLogicalAnd(); e1 = new BinOpNode(op, e1, e2); }
		return e1;
	}

	private ExprNode parseAssignment() {
		ExprNode e1 = parseLogicalOr();
		Token op;
		if ((op = match(
				ASSIGNMENT, ASSIGNMENT_ADD, ASSIGNMENT_SUB, ASSIGNMENT_DIV, ASSIGNMENT_MUL, ASSIGNMENT_AND,
				ASSIGNMENT_XOR, ASSIGNMENT_OR)) != null) {
			ExprNode e2 = parseAssignment();
			e1 = new BinOpNode(op, e1, e2);
		}
		return e1;
	}

	private StmtNode parseStatement() {
		StmtNode stmtNode = null;
		Token token = match(PRINT, IF, WHILE, FOR, LBRACE);
		if (token != null) {
			switch (token.type) {
				case PRINT:
					stmtNode = new PrintNode(new VarNode(require(ID)));
					break;
				case WHILE:
					final ExprNode conditionWhile = parseLogicalCondition();
					StmtNode stmtNodeWhile = parseStatement();
					return new WhileNode(stmtNodeWhile, conditionWhile);
				case FOR:
					require(LPAR);
					final StmtNode assignLeft = parseStatement();
					final ExprNode conditionMiddle = parseExpression();
					require(SEMICOLON);
					final ExprStmtNode operationRight = parseExprStmtNode();
					require(RPAR);
					StmtNode stmtNodeFor = parseStatement();
					return new ForNode(stmtNodeFor, assignLeft, conditionMiddle, operationRight);
				case IF:
					final ExprNode conditionIf = parseLogicalCondition();
					StmtNode stmtNodeIf = parseStatement();
					if (match(ELSE) != null) {
						StmtNode stmtNodeElse = parseStatement();
						return new IfElseNode(conditionIf, stmtNodeIf, stmtNodeElse);
					} else
						return new IfNode(conditionIf, stmtNodeIf);
				case LBRACE:
					List<StmtNode> stmtNodeBlock = new ArrayList<>();
					while (match(RBRACE) == null) {
						StmtNode stmt = parseStatement();
						if (stmt == null)
							error("Failed to match statement.");
						stmtNodeBlock.add(stmt);
					}
					return new BlockStatement(stmtNodeBlock);
			}
		} else {
			if (match(SEMICOLON) == null) {
				stmtNode = parseExprStmtNode();
			} else
				return new EmptyNode();
		}
		require(SEMICOLON);
		return stmtNode;
	}

	private ExprStmtNode parseExprStmtNode() {
		ArrayList<ExprNode> exprNodes = new ArrayList<>();
		do {
			ExprNode exprNode = parseExpression();
			if (exprNode == null)
				error("Failed to match expression");
			exprNodes.add(exprNode);
		} while (match(COMMA) != null);
		return new ExprStmtNode(exprNodes);
	}

	public List<StmtNode> parseProgram() {
		ArrayList<StmtNode> stmtNodeArrayList = new ArrayList<>();
		while (pos < tokens.size())
			stmtNodeArrayList.add(parseStatement());
		return stmtNodeArrayList;
	}

	private ExprNode parseExpression() {
		if (pos >= tokens.size())
			return null;
		return parseAssignment();
	}

	private ExprNode parseLogicalCondition() { require(LPAR); ExprNode e = parseExpression(); require(RPAR); return e; }

}
