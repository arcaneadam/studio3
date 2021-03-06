/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.ide.syncing.ui.navigator.actions;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionConstants;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;

/**
 * @author Michael Xia (mxia@aptana.com)
 */
public class ExtendedActionProvider extends CommonActionProvider {

    private DoubleClickAction fDoubleClickAction;

    public ExtendedActionProvider() {
    }

    @Override
    public void init(ICommonActionExtensionSite aSite) {
        super.init(aSite);

        fDoubleClickAction = new DoubleClickAction(aSite.getViewSite().getShell(),
                (TreeViewer) aSite.getStructuredViewer());
    }

    @Override
    public void fillActionBars(IActionBars actionBars) {
        super.fillActionBars(actionBars);

        actionBars.setGlobalActionHandler(ICommonActionConstants.OPEN, fDoubleClickAction);
    }
}
