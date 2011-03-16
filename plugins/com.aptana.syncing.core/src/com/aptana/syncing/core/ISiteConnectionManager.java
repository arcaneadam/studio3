/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */

package com.aptana.syncing.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.aptana.syncing.core.events.ISiteConnectionListener;

/**
 * @author Max Stepanov
 *
 */
public interface ISiteConnectionManager {

	public void addSiteConnection(ISiteConnection siteConnection);
	public void removeSiteConnection(ISiteConnection siteConnection);
	public void siteConnectionChanged(ISiteConnection siteConnection);

	ISiteConnection createSiteConnection();
	ISiteConnection cloneSiteConnection(ISiteConnection siteConnection) throws CoreException;
	
	public ISiteConnection[] getSiteConnections();

	public void addListener(ISiteConnectionListener listener);
	public void removeListener(ISiteConnectionListener listener);

	public void loadState(IPath path);
	public void saveState(IPath path);
}