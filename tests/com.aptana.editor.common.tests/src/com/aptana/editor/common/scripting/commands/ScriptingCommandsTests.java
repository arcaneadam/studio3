/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.editor.common.scripting.commands;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ScriptingCommandsTests
{

	public static Test suite()
	{
		TestSuite suite = new TestSuite(ScriptingCommandsTests.class.getName());
		//$JUnit-BEGIN$
		suite.addTestSuite(UtilitiesTest.class);
		suite.addTestSuite(TextEditorUtilsTest.class);
		//$JUnit-END$
		return suite;
	}

}
