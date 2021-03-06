/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.parsing;

import java.io.IOException;

import beaver.Parser.TokenStream;
import beaver.Symbol;

public interface IRecoveryStrategy
{
	/**
	 * recover
	 * 
	 * @param parser
	 * @param lastToken
	 * @param currentToken
	 * @param in
	 * @return
	 */
	boolean recover(IParser parser, Symbol lastToken, Symbol currentToken, TokenStream in) throws IOException;
}
