/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.js.parsing.ast;

import beaver.Symbol;

public class JSArrayNode extends JSNode
{
	private Symbol _leftBracket;
	private Symbol _rightBracket;

	/**
	 * JSArrayNode
	 * 
	 * @param elements
	 */
	public JSArrayNode(Symbol leftBracket, Symbol rightBracket, JSNode... elements)
	{
		super(JSNodeTypes.ARRAY_LITERAL, elements);

		this._leftBracket = leftBracket;
		this._rightBracket = rightBracket;
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.editor.js.parsing.ast.JSNode#accept(com.aptana.editor.js.parsing.ast.JSTreeWalker)
	 */
	@Override
	public void accept(JSTreeWalker walker)
	{
		walker.visit(this);
	}

	/**
	 * getLeftBracket
	 * 
	 * @return
	 */
	public Symbol getLeftBracket()
	{
		return this._leftBracket;
	}

	/**
	 * getRightBracket
	 * 
	 * @return
	 */
	public Symbol getRightBracket()
	{
		return this._rightBracket;
	}
}
