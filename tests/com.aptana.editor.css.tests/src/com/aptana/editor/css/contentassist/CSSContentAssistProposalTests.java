/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.css.contentassist;

import com.aptana.editor.css.tests.CSSEditorBasedTests;

/**
 * JSContentAssistProposalTests
 */
public class CSSContentAssistProposalTests extends CSSEditorBasedTests
{
	/**
	 * testFailureAfterColon
	 */
	public void testFailureAfterColon()
	{
		// @formatter:off
		this.checkProposals(
			"contentAssist/failure-after-colon.css",
			"center",
			"inherit",
			"justify",
			"left",
			"right"
		);
		// @formatter:on
	}
}
