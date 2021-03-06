/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.portal.ui.internal.startpage;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;

import com.aptana.portal.ui.browser.AbstractPortalBrowserEditor;

/**
 * Aptana Studio Start-Page browser editor.
 * 
 * @author Shalom Gibly <sgibly@appcelerator.com>
 */
public class StartPageBrowserEditor extends AbstractPortalBrowserEditor
{

	public static final String WEB_BROWSER_EDITOR_ID = "com.aptana.portal.ui.browser.startPage"; //$NON-NLS-1$
	public static final String STUDIO_START_PAGE_URL = "http://www.aptana.com/"; //$NON-NLS-1$

	private static final String TITLE = "Start Page"; //$NON-NLS-1$
	private static final String TITLE_TIP = "Aptana Studio Start Page"; //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * @see com.aptana.portal.ui.browser.AbstractPortalBrowserEditor#init(org.eclipse.ui.IEditorSite,
	 * org.eclipse.ui.IEditorInput)
	 */
	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException
	{
		super.init(site, input);
		setPartName(TITLE);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.part.EditorPart#getTitleToolTip()
	 */
	@Override
	public String getTitleToolTip()
	{
		return TITLE_TIP;
	}

	/*
	 * (non-Javadoc)
	 * @see com.aptana.portal.ui.browser.PortalBrowserEditor#getBaseURLPrefix()
	 */
	protected String getBaseURLPrefix()
	{
		return StartPageUtil.getStartPageURL();
	}
}
