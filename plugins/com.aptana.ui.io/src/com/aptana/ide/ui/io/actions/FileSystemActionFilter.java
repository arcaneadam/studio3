/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */

package com.aptana.ide.ui.io.actions;

import java.io.File;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.IActionFilter;

import com.aptana.ide.ui.io.FileSystemUtils;

/**
 * @author Max Stepanov
 *
 */
public class FileSystemActionFilter implements IActionFilter {

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IActionFilter#testAttribute(java.lang.Object, java.lang.String, java.lang.String)
	 */
	public boolean testAttribute(Object target, String name, String value) {
		if (target instanceof IAdaptable) {
			if ( "isLocal".equals(name)) { //$NON-NLS-1$
				return (((IAdaptable) target).getAdapter(File.class) != null) == toBoolean(value);
			}
			if ( "isWorkspace".equals(name)) { //$NON-NLS-1$
				return (((IAdaptable) target).getAdapter(IResource.class) != null) == toBoolean(value);
			}
			if ( "isFolder".equals(name)) { //$NON-NLS-1$
				return FileSystemUtils.isDirectory(target) == toBoolean(value);
			}
			if ( "isSymlink".equals(name)) { //$NON-NLS-1$
				return  FileSystemUtils.isSymlink(target) == toBoolean(value);
			}			
			if ( "isPrivate".equals(name)) { //$NON-NLS-1$
				return FileSystemUtils.isPrivate(target) == toBoolean(value);
			}
		}
		return false;
	}

	private static boolean toBoolean(Object value) {
		if ( value instanceof Boolean ) {
			 return ((Boolean)value).booleanValue();
		} else if (value instanceof String) {
			return Boolean.parseBoolean((String) value);
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	public static class Factory implements IAdapterFactory {
		
		/* (non-Javadoc)
		 * @see org.eclipse.core.runtime.IAdapterFactory#getAdapter(java.lang.Object, java.lang.Class)
		 */
		public Object getAdapter(Object adaptableObject, Class adapterType) {
			if (adapterType == IActionFilter.class) {
				return new FileSystemActionFilter();
			}
			if (adapterType == IFileStore.class) {
				return ((IAdaptable) adaptableObject).getAdapter(adapterType);
			}
			return null;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.core.runtime.IAdapterFactory#getAdapterList()
		 */
		public Class[] getAdapterList() {
			return new Class[] { IActionFilter.class, IFileStore.class };
		}
	}
}
