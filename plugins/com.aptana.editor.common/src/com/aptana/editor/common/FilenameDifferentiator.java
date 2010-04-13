package com.aptana.editor.common;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.WorkbenchPart;
import org.eclipse.ui.progress.UIJob;

/**
 * This is a special class that listens to editors and makes sure that the tab name is unique. If two tabs share same
 * filename we append additional text to disambiguate them.
 * 
 * @author cwilliams
 */
class FilenameDifferentiator extends UIJob implements IPartListener
{

	/**
	 * Separates original filename from disambiguating name(s)
	 */
	private static final String SEPARATOR = " | "; //$NON-NLS-1$
	private HashMap<String, Set<IEditorPart>> baseNames;

	public FilenameDifferentiator()
	{
		super("Install filename differentiator"); //$NON-NLS-1$
		setSystem(true);
		baseNames = new HashMap<String, Set<IEditorPart>>();
	}

	@Override
	public IStatus runInUIThread(IProgressMonitor monitor)
	{
		// TODO Listen for window open/closes and page add/removal; then register for every window, for every page
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null)
			window.getActivePage().addPartListener(this);
		return Status.OK_STATUS;
	}

	@Override
	public void partActivated(IWorkbenchPart part)
	{
		disambiguate(part);
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part)
	{
		// do nothing
	}

	@Override
	public void partClosed(IWorkbenchPart part)
	{
		if (!(part instanceof IEditorPart))
			return;
		String title = getBaseName(part);
		synchronized (baseNames)
		{
			if (!baseNames.containsKey(title))
				return;
			// Remove from map!
			Set<IEditorPart> parts = baseNames.remove(title);
			parts.remove(part);
			if (!parts.isEmpty())
			{
				baseNames.put(title, parts);
				if (parts.size() == 1)
				{
					// If parts is now of size == 1, we can revert the leftover editor title to the basename!
					IEditorPart leftover = parts.iterator().next();
					setTitle(leftover, title);
				}
			}
		}
	}

	@Override
	public void partDeactivated(IWorkbenchPart part)
	{
		// do nothing
	}

	@Override
	public void partOpened(IWorkbenchPart part)
	{
		disambiguate(part);
	}

	private void disambiguate(IWorkbenchPart part)
	{
		if (!(part instanceof IEditorPart))
			return;
		// First we add to list of names
		String title = getBaseName(part);
		Set<IEditorPart> list;
		synchronized (baseNames)
		{
			if (baseNames.containsKey(title))
			{
				list = baseNames.get(title);
			}
			else
			{
				list = new HashSet<IEditorPart>();
			}
			list.add((IEditorPart) part);
			baseNames.put(title, list);
		}
		// Now we need to disambiguate between all the entries in the list!
		if (list.size() > 1)
		{
			Map<IEditorPart, String> newTitles = getUnambiguousTitles(list);
			for (Map.Entry<IEditorPart, String> entry : newTitles.entrySet())
			{
				setTitle(entry.getKey(), entry.getValue());
			}
		}
	}

	private void setTitle(IEditorPart key, String value)
	{
		try
		{
			Method m = WorkbenchPart.class.getDeclaredMethod("setPartName", String.class); //$NON-NLS-1$
			m.setAccessible(true);
			m.invoke(key, value);
		}
		catch (Exception e)
		{
			CommonEditorPlugin.logError(e);
		}

	}

	protected String getBaseName(IWorkbenchPart part)
	{
		String title = part.getTitle();
		title = title.split(Pattern.quote(SEPARATOR))[0];
		return title;
	}

	private Map<IEditorPart, String> getUnambiguousTitles(Collection<IEditorPart> list)
	{
		Map<IEditorPart, String> returnMap = new HashMap<IEditorPart, String>();

		Map<IEditorPart, IPath> map = new HashMap<IEditorPart, IPath>();
		int min = Integer.MAX_VALUE;
		for (IEditorPart part : list)
		{
			IPath path = getPath(part);
			if (path == null)
				continue;
			min = Math.min(path.segmentCount(), min);
			map.put(part, path);
		}

		// Need to disambiguate the titles!
		for (int i = 2; i <= min; i++)
		{
			returnMap.clear();
			Set<String> curSegments = new HashSet<String>();
			for (Map.Entry<IEditorPart, IPath> entry : map.entrySet())
			{
				IPath path = entry.getValue();
				String segment = path.segment(path.segmentCount() - i);
				if (curSegments.contains(segment))
				{
					break;
				}
				curSegments.add(segment);
				String title = path.lastSegment() + SEPARATOR + segment;
				returnMap.put(entry.getKey(), title);
			}
			// They're all unique, return them all!
			if (curSegments.size() == map.size())
			{
				return returnMap;
			}
		}

		// Something failed! What do we do now?
		return Collections.emptyMap();
	}

	private IPath getPath(IEditorPart otherEditor)
	{
		IEditorInput input = otherEditor.getEditorInput();
		if (input instanceof IPathEditorInput)
		{
			IPathEditorInput pathInput = (IPathEditorInput) input;
			return pathInput.getPath();
		}
		return null;
	}

	public void dispose()
	{
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null)
		{
			window.getActivePage().removePartListener(this);
		}
		baseNames.clear();
		baseNames = null;
	}

}