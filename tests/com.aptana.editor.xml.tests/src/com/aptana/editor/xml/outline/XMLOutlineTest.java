package com.aptana.editor.xml.outline;

import junit.framework.TestCase;

import com.aptana.editor.xml.XMLPlugin;
import com.aptana.editor.xml.parsing.XMLParser;
import com.aptana.parsing.ParseState;

public class XMLOutlineTest extends TestCase
{

	private XMLOutlineContentProvider fContentProvider;
	private XMLOutlineLabelProvider fLabelProvider;

	private XMLParser fParser;

	@Override
	protected void setUp() throws Exception
	{
		fContentProvider = new XMLOutlineContentProvider();
		fLabelProvider = new XMLOutlineLabelProvider();
		fParser = new XMLParser();
	}

	@Override
	protected void tearDown() throws Exception
	{
		if (fContentProvider != null)
		{
			fContentProvider.dispose();
			fContentProvider = null;
		}
		if (fLabelProvider != null)
		{
			fLabelProvider.dispose();
			fLabelProvider = null;
		}
		fParser = null;
	}

	public void testContent() throws Exception
	{
		String source = "<test x=\"100\" y=\"10\"></test>";
		ParseState parseState = new ParseState();
		parseState.setEditState(source, source, 0, 0);
		fParser.parse(parseState);

		Object[] elements = fContentProvider.getElements(parseState.getParseResult());
		assertEquals(1, elements.length);
		assertEquals("test", fLabelProvider.getText(elements[0]));
		assertEquals(XMLPlugin.getImage("icons/element.gif"), fLabelProvider.getImage(elements[0]));
	}
}