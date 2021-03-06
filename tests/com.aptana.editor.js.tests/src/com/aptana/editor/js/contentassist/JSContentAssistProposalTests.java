/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.js.contentassist;

import com.aptana.editor.js.tests.JSEditorBasedTests;

/**
 * JSContentAssistProposalTests
 */
public class JSContentAssistProposalTests extends JSEditorBasedTests
{
	/**
	 * testStringCharCodeAt
	 */
	public void testStringCharCodeAt()
	{
		this.checkProposals("contentAssist/string-charCodeAt.js", "charCodeAt");
	}

	/**
	 * testStringF
	 */
	public void testStringFPrefix()
	{
		this.checkProposals("contentAssist/f-prefix.js", true, true, "false", "finally", "focus", "for", "forward",
				"frames", "function", "Function");
	}

	/**
	 * testStringFunction
	 */
	public void testStringFunction()
	{
		this.checkProposals("contentAssist/function.js", true, true, "function", "Function");
	}

	/**
	 * testStringFunction
	 */
	public void testStringFunctionCaseOrder()
	{
		// Commented out for the moment until we resolve an issue with indexing
		// this.checkProposals("contentAssist/function-case-order.js", true, true, "focus", "foo", "fooa", "foob",
		// "for",
		// "forward");
	}

	/**
	 * testStringFunction
	 */
	public void testStringThis()
	{
		this.checkProposals("contentAssist/this.js", true, true, "this", "throw");
	}

	/**
	 * testStringD
	 */
	public void testStringDPrefix()
	{
		this.checkProposals("contentAssist/d-prefix.js", true, true, "default", "defaultStatus", "delete", "do",
				"document", "Date");
	}

	/**
	 * testBug_Math
	 */
	public void testBug_Math()
	{
		// @formatter:off
		this.checkProposals(
			"contentAssist/math.js",
			"E",
			"LN10",
			"LN2",
			"LOG10E",
			"LOG2E",
			"PI",
			"SQRT1_2",
			"SQRT2",
			"abs",
			"acos",
			"asin",
			"atan",
			"atan2",
			"ceil",
			"cos",
			"exp",
			"floor",
			"log",
			"max",
			"min",
			"pow",
			"random",
			"round",
			"sin",
			"sqrt",
			"tan"
		);
		// @formatter:on
	}

	/**
	 * testBug_VarAssignWithEndingDot
	 */
	public void testBug_VarAssignWithEndingDot()
	{
		// @formatter:off
		this.checkProposals(
			"contentAssist/var-assign-with-ending-dot.js",
			"E",
			"LN10",
			"LN2",
			"LOG10E",
			"LOG2E",
			"PI",
			"SQRT1_2",
			"SQRT2",
			"abs",
			"acos",
			"asin",
			"atan",
			"atan2",
			"ceil",
			"cos",
			"exp",
			"floor",
			"log",
			"max",
			"min",
			"pow",
			"random",
			"round",
			"sin",
			"sqrt",
			"tan"
		);
		// @formatter:on
	}

	/**
	 * testObjectLiteral
	 */
	public void testObjectLiteral()
	{
		// @formatter:off
		this.checkProposals(
			"contentAssist/object-literal.js",
			"flag",
			"number"
		);
		// @formatter:on
	}
}
