/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.git.core.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.contentobjects.jnotify.IJNotify;
import net.contentobjects.jnotify.JNotifyAdapter;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.aptana.core.epl.ReadWriteMonitor;
import com.aptana.core.util.EclipseUtil;
import com.aptana.core.util.IOUtil;
import com.aptana.core.util.StringUtil;
import com.aptana.filewatcher.FileWatcher;
import com.aptana.git.core.GitPlugin;
import com.aptana.git.core.IPreferenceConstants;
import com.aptana.git.core.model.GitRef.TYPE;

public class GitRepository
{

	/**
	 * Order branches alphabetically, with local branches all appearing before remote ones.
	 * 
	 * @author cwilliams
	 */
	private static final class BranchNameComparator implements Comparator<String>
	{
		public int compare(String o1, String o2)
		{
			if (o1.contains("/") && !o2.contains("/")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				return 1;
			}
			if (o2.contains("/") && !o1.contains("/")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				return -1;
			}
			return o1.compareTo(o2);
		}
	}

	private static final String GITHUB_COM = "github.com"; //$NON-NLS-1$

	/**
	 * Filename to store ignores of files.
	 */
	public static final String GITIGNORE = ".gitignore"; //$NON-NLS-1$
	/**
	 * File used to associate SHAs and refs when git pack-refs has been used.
	 */
	private static final String PACKED_REFS = "packed-refs"; //$NON-NLS-1$
	/**
	 * The file used
	 */
	private static final String COMMIT_EDITMSG = "COMMIT_EDITMSG"; //$NON-NLS-1$
	/**
	 * File hoplding the concatenated commit messages from merge --squash
	 */
	private static final String SQUASH_MSG = "SQUASH_MSG"; //$NON-NLS-1$
	/**
	 * The most important file in git. This holds the current file state. When this changes, the state of files in the
	 * repo has changed.
	 */
	private static final String INDEX = "index"; //$NON-NLS-1$
	/**
	 * File created prior to merges (which happen as part of pull, which is just fetch + merge).
	 */
	private static final String ORIG_HEAD = "ORIG_HEAD"; //$NON-NLS-1$
	/**
	 * A file created when we hit merge conflicts that need to be manually resolved.
	 */
	private static final String MERGE_HEAD_FILENAME = "MERGE_HEAD"; //$NON-NLS-1$
	static final String HEAD = "HEAD"; //$NON-NLS-1$

	public static final String GIT_DIR = ".git"; //$NON-NLS-1$

	/**
	 * Regexp used to grab list of remote names out of .git/config.
	 */
	private final static Pattern fgRemoteNamePattern = Pattern.compile("\\[remote \"(.+?)\"\\]"); //$NON-NLS-1$

	/**
	 * Regexp used to grab list of remote URLs out of .git/config.
	 */
	private final static Pattern fgRemoteURLPattern = Pattern
			.compile("\\[remote \"(.+?)\"\\](\\s+[^\\[]+)?\\s+url = (.+?)\\s+"); //$NON-NLS-1$

	/**
	 * Monitor to allow simultaneous read processes, but only one "write" process which alters the repo/index.
	 */
	private ReadWriteMonitor monitor = new ReadWriteMonitor();

	private List<GitRevSpecifier> branches;
	Map<String, List<GitRef>> refs;
	private URI fileURL;
	private GitRevSpecifier _headRef;
	private GitIndex index;
	private boolean hasChanged;
	private GitRevSpecifier currentBranch;
	private Set<Integer> fileWatcherIds = new HashSet<Integer>();
	private int remoteDirCreationWatchId = -1;
	private HashSet<IGitRepositoryListener> listeners;

	GitRepository(URI fileURL)
	{
		this.fileURL = fileURL;
		this.branches = new ArrayList<GitRevSpecifier>();
		reloadRefs();
		readCurrentBranch();
		try
		{
			// FIXME When actions are taken through our model/UI we end up causing multiple refreshes for index changes
			// index appears to change on commit/stage/unstage/pull
			// Add listener for changes in HEAD (i.e. switched branches), and index
			fileWatcherIds.add(FileWatcher.addWatch(gitDirPath().toOSString(), IJNotify.FILE_ANY, false,
					new JNotifyAdapter()
					{

						private Set<String> filesToWatch;

						@Override
						public void fileRenamed(int wd, String rootPath, String oldName, String newName)
						{
							if (newName == null || !filesToWatch().contains(newName))
								return;
							if (newName.equals(HEAD))
								checkForBranchChange();
							else if (newName.equals(INDEX) || newName.equals(COMMIT_EDITMSG))
								refreshIndex();
						}

						@Override
						public void fileCreated(int wd, String rootPath, String name)
						{
							if (name == null)
								return;
							if (name.equals(INDEX))
								refreshIndex();
							else if (name.equals(ORIG_HEAD)) // this is done before merges (or pulls, which are just
																// fetch + merge)
								firePullEvent(); // we're conflating the two events here because I don't have the ideas
													// separated in the listeners yet.
						}

						@Override
						public void fileDeleted(int wd, String rootPath, String name)
						{
							if (name != null && name.equals(INDEX))
								refreshIndex();
						}

						private Set<String> filesToWatch()
						{
							if (filesToWatch == null)
							{
								filesToWatch = new HashSet<String>();
								filesToWatch.add(HEAD);
								filesToWatch.add(INDEX);
								filesToWatch.add(COMMIT_EDITMSG);
							}
							return filesToWatch;
						}

						@Override
						public void fileModified(int wd, String rootPath, String name)
						{
							if (name == null || !filesToWatch().contains(name))
								return;
							if (name.equals(HEAD))
								checkForBranchChange();
							else if (name.equals(INDEX) || name.equals(COMMIT_EDITMSG))
								refreshIndex();
						}

						// Do long running work in another thread/job so we don't tie up the jnotify locks!
						private void refreshIndex()
						{
							index().refreshAsync();
						}

						protected void checkForBranchChange()
						{
							Job job = new Job("Checking for current branch switch") //$NON-NLS-1$
							{
								@Override
								protected IStatus run(IProgressMonitor monitor)
								{
									String oldBranchName = currentBranch.simpleRef().shortName();
									_headRef = null;
									readCurrentBranch();
									String newBranchName = currentBranch.simpleRef().shortName();
									if (oldBranchName.equals(newBranchName))
										return Status.OK_STATUS;
									fireBranchChangeEvent(oldBranchName, newBranchName);
									return Status.OK_STATUS;
								}
							};
							job.setSystem(!EclipseUtil.showSystemJobs());
							job.schedule();
						}
					}));

			// Add listener for remotes
			if (gitFile(GitRef.REFS_REMOTES).isDirectory())
			{
				addRemotesFileWatcher();
			}
			else
			{
				// If refs/remote doesn't exist, we need to add a listener on "refs" for creation of remotes!
				remoteDirCreationWatchId = FileWatcher.addWatch(gitFile(GitRef.REFS).getAbsolutePath(),
						IJNotify.FILE_CREATED, false, new JNotifyAdapter()
						{
							public void fileCreated(int wd, String rootPath, String name)
							{
								if (name != null && name.equals("remotes")) //$NON-NLS-1$
								{
									try
									{
										addRemotesFileWatcher();
									}
									catch (JNotifyException e)
									{
										GitPlugin.logError(e.getMessage(), e);
									}

									if (remoteDirCreationWatchId == -1)
										return;
									// Remove this watcher!
									Job job = new Job("Removing file watcher on remotes dir creation") //$NON-NLS-1$
									{
										@Override
										protected IStatus run(IProgressMonitor monitor)
										{
											try
											{
												FileWatcher.removeWatch(remoteDirCreationWatchId);
											}
											catch (JNotifyException e)
											{
												GitPlugin.logError(e.getMessage(), e);
											}
											return Status.OK_STATUS;
										}
									};
									job.setSystem(!EclipseUtil.showSystemJobs());
									job.schedule();
								}
							}
						});
				fileWatcherIds.add(remoteDirCreationWatchId);
			}

			// Add listener for added/removed branches
			fileWatcherIds.add(FileWatcher.addWatch(gitFile(GitRef.REFS_HEADS).getAbsolutePath(), IJNotify.FILE_CREATED
					| IJNotify.FILE_DELETED, false, new JNotifyAdapter()
			{

				@Override
				public void fileDeleted(int wd, String rootPath, final String name)
				{
					if (name == null || name.endsWith(".lock")) //$NON-NLS-1$
						return;
					// Remove branch in model!
					final GitRevSpecifier rev = new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_HEADS + name));
					branches.remove(rev);

					Job job = new Job("Handle branch removal") //$NON-NLS-1$
					{
						@Override
						protected IStatus run(IProgressMonitor monitor)
						{
							// the branch may in fact still exists
							reloadRefs();
							// only fires the event if the branch is indeed removed
							if (!branches.contains(rev))
							{
								fireBranchRemovedEvent(name);
							}
							return Status.OK_STATUS;
						}
					};
					job.setSystem(!EclipseUtil.showSystemJobs());
					job.schedule();
				}

				@Override
				public void fileCreated(int wd, String rootPath, final String name)
				{
					if (name == null || name.endsWith(".lock")) //$NON-NLS-1$
						return;
					// a branch has been added
					addBranch(new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_HEADS + name)));

					Job job = new Job("Checking if HEAD changed") //$NON-NLS-1$
					{
						@Override
						protected IStatus run(IProgressMonitor monitor)
						{
							fireBranchAddedEvent(name);
							// Check if our HEAD changed
							String oldBranchName = currentBranch.simpleRef().shortName();
							if (oldBranchName.equals(name))
								return Status.OK_STATUS;
							_headRef = null;
							readCurrentBranch();
							fireBranchChangeEvent(oldBranchName, name);
							return Status.OK_STATUS;
						}
					};
					job.setSystem(!EclipseUtil.showSystemJobs());
					job.schedule();
				}
			}));
		}
		catch (JNotifyException e)
		{
			GitPlugin.logError(e.getMessage(), e);
		}
	}

	/**
	 * @throws JNotifyException
	 */
	private void addRemotesFileWatcher() throws JNotifyException
	{
		fileWatcherIds.add(FileWatcher.addWatch(gitFile(GitRef.REFS_REMOTES).getAbsolutePath(), IJNotify.FILE_ANY,
				true, new JNotifyListener()
				{

					public void fileRenamed(int wd, String rootPath, String oldName, String newName)
					{
						if (newName == null)
							return;
						if (isProbablyBranch(newName))
						{
							// FIXME Can't tell if we pushed or pulled unless we look at sha tree/commit list. For
							// now,
							// seems harmless to fire both.
							Job job = new Job("Firing pull and push event") //$NON-NLS-1$
							{
								@Override
								protected IStatus run(IProgressMonitor monitor)
								{
									firePullEvent();
									firePushEvent();
									return Status.OK_STATUS;
								}
							};
							job.setSystem(!EclipseUtil.showSystemJobs());
							job.schedule();
						}
					}

					// Determine if filename is referring to a remote branch, and not the remote itself.
					private boolean isProbablyBranch(String newName)
					{
						return newName != null && newName.indexOf(File.separator) != -1 && !newName.endsWith(".lock"); //$NON-NLS-1$
					}

					public void fileModified(int wd, String rootPath, String name)
					{
						if (name == null)
							return;
						if (isProbablyBranch(name))
						{
							// FIXME Can't tell if we pushed or pulled unless we look at sha tree/commit list. For
							// now,
							// seems harmless to fire both.
							Job job = new Job("Firing pull and push event") //$NON-NLS-1$
							{
								@Override
								protected IStatus run(IProgressMonitor monitor)
								{
									firePullEvent();
									firePushEvent();
									return Status.OK_STATUS;
								}
							};
							job.setSystem(!EclipseUtil.showSystemJobs());
							job.schedule();
						}
					}

					public void fileDeleted(int wd, String rootPath, final String name)
					{
						// if path is longer than one segment, then remote branch was deleted. Means we probably
						// pulled.
						if (isProbablyBranch(name))
						{
							branches.remove(new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_REMOTES + name)));
							Job job = new Job("Firing branch removed and pull event") //$NON-NLS-1$
							{
								@Override
								protected IStatus run(IProgressMonitor monitor)
								{
									fireBranchRemovedEvent(name);
									firePullEvent();
									return Status.OK_STATUS;
								}
							};
							job.setSystem(!EclipseUtil.showSystemJobs());
							job.schedule();
						}
					}

					public void fileCreated(int wd, String rootPath, final String name)
					{
						if (isProbablyBranch(name))
						{
							// if path is longer than one segment, then remote branch was created.
							addBranch(new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_REMOTES + name)));
							// Since we suddenly know about a new remote branch, we probably pulled.

							Job job = new Job("Firing branche added and pull event") //$NON-NLS-1$
							{
								@Override
								protected IStatus run(IProgressMonitor monitor)
								{
									fireBranchAddedEvent(name);
									firePullEvent();
									return Status.OK_STATUS;
								}
							};
							job.setSystem(!EclipseUtil.showSystemJobs());
							job.schedule();
						}
					}
				}));
	}

	public IPath workingDirectory()
	{
		if (gitDirPath().lastSegment().equals(GIT_DIR))
		{
			return gitDirPath().removeLastSegments(1);
		}
		IStatus result = execute(ReadWrite.READ, "rev-parse", "--is-inside-work-tree"); //$NON-NLS-1$ //$NON-NLS-2$
		if (result != null && result.isOK() && result.getMessage().trim().equals("true")) //$NON-NLS-1$
		{
			return GitExecutable.instance().path(); // FIXME This doesn't seem right....
		}

		return null;
	}

	/**
	 * Returns the set of local branches that this repo knows about.
	 * 
	 * @return
	 */
	public Set<String> localBranches()
	{
		return branches(GitRef.TYPE.HEAD);
	}

	/**
	 * Returns the set of remote branches that this repo knows about.
	 * 
	 * @return
	 */
	public Set<String> remoteBranches()
	{
		return branches(GitRef.TYPE.REMOTE);
	}

	/**
	 * Returns the set of remotes attached to this repository. Equivalent of 'git remote'. For performance reasons, we
	 * parse the list of remotes from .git/config file rather than run 'git remote' process.§We can't just look at
	 * .git/remotes, because entries only appear there after a push has been performed.
	 * 
	 * @return
	 */
	public Set<String> remotes()
	{
		Set<String> set = new HashSet<String>();
		monitor.enterRead();
		try
		{
			File configFile = gitFile("config"); //$NON-NLS-1$
			String contents = IOUtil.read(new FileInputStream(configFile));
			Matcher m = fgRemoteNamePattern.matcher(contents);
			while (m.find())
			{
				set.add(m.group(1));
			}
		}
		catch (FileNotFoundException e)
		{
			GitPlugin.logError(e);
		}
		finally
		{
			monitor.exitRead();
		}
		return set;
	}

	/**
	 * Returns a sorted set of local and remote branches known to this repo. Orders local branches before remote
	 * branches.
	 * 
	 * @return
	 */
	public Set<String> allBranches()
	{
		return branches(GitRef.TYPE.HEAD, GitRef.TYPE.REMOTE);
	}

	private Set<String> branches(GitRef.TYPE... types)
	{
		if (types == null || types.length == 0)
			return Collections.emptySet();
		Set<GitRef.TYPE> validTypes = new HashSet<GitRef.TYPE>(Arrays.asList(types));

		// Sort branches. Make sure local ones always come before remote
		SortedSet<String> allBranches = new TreeSet<String>(new BranchNameComparator());

		for (GitRevSpecifier revSpec : branches)
		{
			if (!revSpec.isSimpleRef())
				continue;
			GitRef ref = revSpec.simpleRef();
			if (ref == null || ref.type() == null)
				continue;
			for (GitRef.TYPE string : types)
			{
				if (ref.type().equals(string))
					break;
			}
			if (!validTypes.contains(ref.type()))
				continue;
			// Skip these magical "*.lock" files
			if (ref.type() == TYPE.HEAD && ref.shortName().endsWith(".lock")) //$NON-NLS-1$
				continue;
			allBranches.add(ref.shortName());
		}
		return allBranches;
	}

	/**
	 * Switches the current working branch on the repo. 'git checkout <branchName>'
	 * 
	 * @param branchName
	 *            the new branch to use as the working branch
	 * @return true if the switch happened. false otherwise.
	 */
	public IStatus switchBranch(String branchName, IProgressMonitor monitor)
	{
		if (branchName == null)
		{
			return new Status(IStatus.ERROR, GitPlugin.PLUGIN_ID, "Branch to switch to must be provided.");
		}
		SubMonitor sub = SubMonitor.convert(monitor, 4);
		try
		{
			// Before switching branches, check for existence of every open project attached to this repo on the new
			// branch!
			// If it doesn't exist, close the project first!
			// if we fail to switch branches, re-open the ones we auto-closed!
			final Set<IProject> projectsNotExistingOnNewBranch = getProjectsThatDontExistOnBranch(branchName,
					sub.newChild(1));
			// Now close all of the affectedProjects.
			closeProjects(projectsNotExistingOnNewBranch, sub.newChild(1));

			String oldBranchName = currentBranch.simpleRef().shortName();
			IStatus result = execute(GitRepository.ReadWrite.WRITE, "checkout", branchName); //$NON-NLS-1$
			sub.worked(1);
			if (result == null || !result.isOK())
			{
				openProjects(projectsNotExistingOnNewBranch, sub.newChild(1));
				return result;
			}
			_headRef = null;
			readCurrentBranch();
			fireBranchChangeEvent(oldBranchName, branchName);
			sub.worked(1);
			return result;
		}
		finally
		{
			sub.done();
		}
	}

	private void openProjects(Set<IProject> projects, IProgressMonitor monitor)
	{
		if (projects == null)
		{
			return;
		}
		SubMonitor sub = SubMonitor.convert(monitor, projects.size());
		for (IProject project : projects)
		{
			try
			{
				project.open(sub.newChild(1));
			}
			catch (CoreException e)
			{
				GitPlugin.logError(e);
			}
		}
		sub.done();
	}

	private void closeProjects(final Set<IProject> projects, IProgressMonitor monitor)
	{
		if (projects == null)
		{
			return;
		}
		SubMonitor sub = SubMonitor.convert(monitor, projects.size());
		for (IProject project : projects)
		{
			try
			{
				project.close(sub.newChild(1));
			}
			catch (CoreException e)
			{
				GitPlugin.logError(e);
			}
		}
		sub.done();
	}

	private Set<IProject> getProjectsThatDontExistOnBranch(final String branchName, IProgressMonitor monitor)
	{
		IPath workingDir = workingDirectory();
		// If root of git is root of a project, no need to check.
		IContainer c = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(workingDir);
		if (c instanceof IProject)
		{
			return Collections.emptySet();
		}
		// Now filter down to projects underneath the repo!
		List<IProject> beneathRepo = new ArrayList<IProject>();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
		{
			if (workingDir.isPrefixOf(project.getLocation()))
			{
				beneathRepo.add(project);
			}
		}
		// Unlikely this woudl ever happen, but if there are no projects under this repo, bail
		if (beneathRepo.isEmpty())
		{
			return Collections.emptySet();
		}

		// Now do a batch check against all the projects underneath our repo
		Set<IProject> projectsNotExistingOnNewBranch = new HashSet<IProject>();
		StringBuilder input = new StringBuilder();
		for (IProject project : beneathRepo)
		{
			input.append(branchName).append(':')
					.append(relativePath(project).append(IProjectDescription.DESCRIPTION_FILE_NAME).toPortableString())
					.append('\n');
		}
		this.monitor.enterRead();
		IStatus result = GitExecutable.instance().runInBackground(input.toString(), workingDir,
				"cat-file", "--batch-check"); //$NON-NLS-1$ //$NON-NLS-2$
		this.monitor.exitRead();
		if (result.isOK())
		{
			String output = result.getMessage();
			String[] lines = output.split("\r?\n|\r"); //$NON-NLS-1$
			int lineNum = 0;
			for (String line : lines)
			{
				if (line.endsWith(" missing")) //$NON-NLS-1$
				{
					projectsNotExistingOnNewBranch.add(beneathRepo.get(lineNum));
				}
				lineNum++;
			}
		}
		return projectsNotExistingOnNewBranch;
	}

	private void readCurrentBranch()
	{
		this.currentBranch = addBranch(headRef());
	}

	private boolean reloadRefs()
	{
		_headRef = null;
		boolean ret = false;

		refs = new HashMap<String, List<GitRef>>();

		IStatus result = execute(ReadWrite.READ, "for-each-ref", //$NON-NLS-1$
				"--format=%(refname) %(objecttype) %(objectname) %(*objectname)", "refs"); //$NON-NLS-1$ //$NON-NLS-2$

		String output = result.getMessage();
		List<String> lines = StringUtil.tokenize(output, "\n"); //$NON-NLS-1$

		for (String line : lines)
		{
			// If its an empty line, skip it (e.g. with empty repositories)
			if (line.length() == 0)
				continue;

			List<String> components = StringUtil.tokenize(line, " "); //$NON-NLS-1$

			// First do the ref matching. If this ref is new, add it to our ref list
			GitRef newRef = GitRef.refFromString(components.get(0));
			GitRevSpecifier revSpec = new GitRevSpecifier(newRef);
			if (!addBranch(revSpec).equals(revSpec))
				ret = true;

			// Also add this ref to the refs list
			addRef(newRef, components);
		}

		// Add an "All branches" option in the branches list
		addBranch(GitRevSpecifier.allBranchesRevSpec());
		addBranch(GitRevSpecifier.localBranchesRevSpec());

		return ret;
	}

	private GitRevSpecifier addBranch(GitRevSpecifier rev)
	{
		if (rev.parameters().isEmpty())
			rev = headRef();

		// First check if the branch doesn't exist already
		for (GitRevSpecifier r : branches)
			if (rev.equals(r))
				return r;

		branches.add(rev);
		// TODO Fire a branchAddedEvent?
		return rev;
	}

	public GitRevSpecifier headRef()
	{
		if (_headRef != null)
			return _headRef;

		String branch = parseSymbolicReference(HEAD);
		if (branch != null && branch.startsWith(GitRef.REFS_HEADS))
			_headRef = new GitRevSpecifier(GitRef.refFromString(branch));
		else
			_headRef = new GitRevSpecifier(GitRef.refFromString(HEAD));

		return _headRef;
	}

	private String parseSymbolicReference(String reference)
	{
		IStatus result = execute(ReadWrite.READ, "symbolic-ref", "-q", reference); //$NON-NLS-1$ //$NON-NLS-2$
		if (result == null || !result.isOK())
		{
			return null;
		}
		String ref = result.getMessage();
		if (ref != null && ref.startsWith(GitRef.REFS))
		{
			return ref;
		}

		return null;
	}

	private void addRef(GitRef ref, List<String> components)
	{
		String type = components.get(1);

		String sha;
		if (type.equals(GitRef.TYPE.TAG) && components.size() == 4)
			sha = components.get(3);
		else
			sha = components.get(2);

		List<GitRef> curRefs = refs.get(sha);
		if (curRefs != null)
		{
			curRefs.add(ref);
		}
		else
		{
			curRefs = new ArrayList<GitRef>();
			curRefs.add(ref);
			refs.put(sha, curRefs);
		}
	}

	/**
	 * get the name of the current branch as a string
	 * 
	 * @return
	 */
	public String currentBranch()
	{
		if (currentBranch == null)
			return null;
		return currentBranch.simpleRef().shortName();
	}

	/**
	 * Return the model encapsulating the index for this repo.
	 * 
	 * @return
	 */
	public synchronized GitIndex index()
	{
		if (index == null)
		{
			index = new GitIndex(this, workingDirectory());
			index.refresh(false, new NullProgressMonitor()); // Don't want to call back to fireIndexChangeEvent yet!
		}
		return index;
	}

	private void fireBranchChangeEvent(String oldBranchName, String newBranchName)
	{
		if (listeners == null || listeners.isEmpty())
			return;
		BranchChangedEvent e = new BranchChangedEvent(this, oldBranchName, newBranchName);
		for (IGitRepositoryListener listener : listeners)
			listener.branchChanged(e);
	}

	private void fireBranchRemovedEvent(String oldBranchName)
	{
		if (listeners == null || listeners.isEmpty())
			return;
		BranchRemovedEvent e = new BranchRemovedEvent(this, oldBranchName);
		for (IGitRepositoryListener listener : listeners)
			listener.branchRemoved(e);
	}

	private void fireBranchAddedEvent(String newBranchName)
	{
		if (listeners == null || listeners.isEmpty())
			return;
		BranchAddedEvent e = new BranchAddedEvent(this, newBranchName);
		for (IGitRepositoryListener listener : listeners)
			listener.branchAdded(e);
	}

	void fireIndexChangeEvent(Collection<ChangedFile> preChangeFiles, Collection<ChangedFile> changedFiles)
	{
		if (listeners == null || listeners.isEmpty())
			return;

		IndexChangedEvent e = new IndexChangedEvent(this, preChangeFiles, changedFiles);
		// If there's no diff, don't even fire the event
		if (!e.hasDiff())
			return;
		for (IGitRepositoryListener listener : listeners)
			listener.indexChanged(e);
	}

	public void lazyReload()
	{
		if (!hasChanged)
			return;

		reloadRefs();
		hasChanged = false;
	}

	void hasChanged()
	{
		hasChanged = true;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof GitRepository))
			return false;
		GitRepository other = (GitRepository) obj;
		return fileURL.getPath().equals(other.fileURL.getPath());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return fileURL.getPath().hashCode();
	}

	/**
	 * Return the list of commits the local copy of a branch is ahead of the remote tracking branch.
	 * 
	 * @param branchName
	 * @return null if there's no tracking remote branch
	 */
	public String[] commitsAhead(String branchName)
	{
		GitRef remote = matchingRemoteBranch(branchName);
		if (remote == null)
		{
			return null;
		}
		return index().commitsBetween(remote.ref(), GitRef.REFS_HEADS + branchName);
	}

	/**
	 * Grabs the remote branch that the passed in local branch is tracking. If tracking is not set up, we'll also try to
	 * find the "matching" branch on "origin". See http://www.kernel.org/pub/software/scm/git/docs/git-push.html#OPTIONS
	 * and http://www.kernel.org/pub/software/scm/git/docs/git-push.html#_examples for details on why we're doing this
	 * (because git does).
	 * 
	 * @param localBranchName
	 * @return
	 */
	private GitRef matchingRemoteBranch(String localBranchName)
	{
		GitRef remote = remoteTrackingBranch(localBranchName);
		if (remote != null)
		{
			return remote;
		}
		String remoteMatchingName = "origin/" + localBranchName; //$NON-NLS-1$
		// If tracking is not set up, git still checks "origin" remote for matching name
		if (remoteBranches().contains(remoteMatchingName))
		{
			return GitRef.refFromString(GitRef.REFS_REMOTES + remoteMatchingName);
		}
		return null;
	}

	public ChangedFile getChangedFileForResource(IResource resource)
	{
		return index().getChangedFileForResource(resource);
	}

	/**
	 * Return the list of commits the local copy of a branch is behind the remote tracking branch.
	 * 
	 * @param branchName
	 * @return null if there's no tracking remote branch
	 */
	public String[] commitsBehind(String branchName)
	{
		GitRef remote = matchingRemoteBranch(branchName);
		if (remote == null)
		{
			return null;
		}
		return index().commitsBetween(GitRef.REFS_HEADS + branchName, remote.ref());
	}

	public boolean isDirty()
	{
		return index().isDirty();
	}

	/**
	 * Returns the remote tracking branch name for the branch passed in. Returns null if there is none.
	 * 
	 * @param branchName
	 * @return
	 */
	private GitRef remoteTrackingBranch(String branchName)
	{
		// Given a local branch name (/refs/head/*), we need to track back to the remote + branch.
		// TODO Store the config contents and only read it again when last mod changes?
		File configFile = gitFile("config"); //$NON-NLS-1$
		String contents = ""; //$NON-NLS-1$
		try
		{
			contents = IOUtil.read(new FileInputStream(configFile));
		}
		catch (FileNotFoundException e)
		{
			GitPlugin.logError(e);
		}

		int index = contents.indexOf("merge = " + GitRef.REFS_HEADS + branchName); //$NON-NLS-1$
		if (index == -1)
		{
			return null;
		}
		int precedingBracket = contents.lastIndexOf('[', index);
		if (precedingBracket == -1)
		{
			precedingBracket = 0;
		}
		int trailingBracket = contents.indexOf('[', index);
		if (trailingBracket == -1)
		{
			trailingBracket = contents.length();
		}
		String branchDetails = contents.substring(precedingBracket, trailingBracket);
		String remoteBranchName = null;
		String remoteName = "origin"; //$NON-NLS-1$
		String[] lines = branchDetails.split("\\r?\\n|\\r"); //$NON-NLS-1$
		for (String line : lines)
		{
			line = line.trim();
			if (line.startsWith("remote = ")) //$NON-NLS-1$
			{
				remoteName = line.substring(9);
			}
			else if (line.startsWith("[branch \"")) //$NON-NLS-1$
			{
				remoteBranchName = line.substring(9, line.length() - 2);
			}
		}
		if (remoteBranchName == null)
		{
			return null;
		}
		return GitRef.refFromString(GitRef.REFS_REMOTES + remoteName + "/" + remoteBranchName); //$NON-NLS-1$
	}

	/**
	 * Returns the set of URLs for all remotes. For performance reasons, we read the .git/config file in and then run a
	 * regexp to search for all remote's URLs. Takes roughly 1ms, versus running a git config regexp process which takes
	 * about 45-55ms per remote.
	 * 
	 * @return
	 */
	public Set<String> remoteURLs()
	{
		Set<String> remoteURLs = new HashSet<String>();
		monitor.enterRead();
		try
		{
			File configFile = gitFile("config"); //$NON-NLS-1$
			String contents = IOUtil.read(new FileInputStream(configFile));
			Matcher m = fgRemoteURLPattern.matcher(contents);
			while (m.find())
			{
				remoteURLs.add(m.group(3));
			}
		}
		catch (FileNotFoundException e)
		{
			GitPlugin.logError(e);
		}
		finally
		{
			monitor.exitRead();
		}
		return remoteURLs;
	}

	/**
	 * @param branchName
	 *            Name of the new branch
	 * @param track
	 *            Whether this branch should track the start point
	 * @param startPoint
	 *            branch name, commit id, or ref/tag to create the branch off of. Null/empty assumes "HEAD"
	 * @return
	 */
	public boolean createBranch(String branchName, boolean track, String startPoint)
	{
		List<String> args = new ArrayList<String>();
		args.add("branch"); //$NON-NLS-1$
		if (track)
		{
			args.add("--track"); //$NON-NLS-1$
		}
		args.add(branchName);
		if (startPoint != null && startPoint.trim().length() > 0)
		{
			args.add(startPoint);
		}
		IStatus result = execute(GitRepository.ReadWrite.WRITE, args.toArray(new String[args.size()]));
		if (result == null || !result.isOK())
		{
			return false;
		}
		// Add branch to list in model!
		addBranch(new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_HEADS + branchName)));
		fireBranchAddedEvent(branchName);
		return true;
	}

	public IStatus deleteBranch(String branchName)
	{
		return deleteBranch(branchName, false);
	}

	public IStatus deleteBranch(String branchName, boolean force)
	{
		List<String> args = new ArrayList<String>();
		args.add("branch"); //$NON-NLS-1$
		if (!force)
		{
			args.add("-d"); //$NON-NLS-1$
		}
		else
		{
			args.add("-D"); //$NON-NLS-1$
		}
		args.add(branchName);

		IStatus result = execute(GitRepository.ReadWrite.WRITE, args.toArray(new String[args.size()]));
		if (!result.isOK())
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), result.getCode(), result.getMessage(), null);
		}
		// Remove branch in model!
		branches.remove(new GitRevSpecifier(GitRef.refFromString(GitRef.REFS_HEADS + branchName)));
		fireBranchRemovedEvent(branchName);
		return Status.OK_STATUS;
	}

	public boolean validBranchName(String branchName)
	{
		IStatus result = execute(GitRepository.ReadWrite.READ, "check-ref-format", GitRef.REFS_HEADS + branchName); //$NON-NLS-1$
		return result != null && result.isOK();
	}

	public IStatus deleteFile(String filePath)
	{
		IStatus result = execute(GitRepository.ReadWrite.WRITE, "rm", "-f", filePath); //$NON-NLS-1$ //$NON-NLS-2$
		if (result == null)
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), "Failed to execute git rm -f"); //$NON-NLS-1$
		}
		if (!result.isOK())
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), result.getCode(), result.getMessage(), null);
		}
		index().refreshAsync();
		return Status.OK_STATUS;
	}

	/**
	 * Enum used to denote if a git process reads or writes(modifies) the git index/repo.
	 * 
	 * @author cwilliams
	 */
	public enum ReadWrite
	{
		READ, WRITE
	}

	/**
	 * Execute a git process, specifying the arguments and whether we should lock for read or write.
	 * 
	 * @param readOrWrite
	 *            if the process modifies the index or repo, it should be marked WRITE (only one at a time, no READS
	 *            concurrently either), otherwise use READ (which we can have multiple in parallel)
	 * @param args
	 * @return
	 */
	public IStatus execute(ReadWrite readOrWrite, String... args)
	{
		return execute(readOrWrite, null, args);
	}

	/**
	 * Sets up the ENV so we can properly hit remotes over SSH/HTTPS.
	 * 
	 * @param readOrWrite
	 *            if the process modifies the index or repo, it should be marked WRITE (only one at a time, no READS
	 *            concurrently either), otherwise use READ (which we can have multiple in parallel)
	 * @param args
	 * @return
	 */
	IStatus executeWithPromptHandling(ReadWrite readOrWrite, String... args)
	{
		return execute(readOrWrite, GitExecutable.getShellEnvironment(), args);
	}

	private IStatus execute(ReadWrite readOrWrite, Map<String, String> env, String... args)
	{
		switch (readOrWrite)
		{
			case READ:
				monitor.enterRead();
				break;

			case WRITE:
				monitor.enterWrite();
				break;
		}
		IStatus result = GitExecutable.instance().runInBackground(workingDirectory(), env, args);
		switch (readOrWrite)
		{
			case READ:
				monitor.exitRead();
				break;

			case WRITE:
				monitor.exitWrite();
				break;
		}
		return result;
	}

	IStatus executeWithInput(String input, String... args)
	{
		// All of these processes appear to be write, so just hard-code that
		monitor.enterWrite();
		IStatus result = GitExecutable.instance().runInBackground(input, workingDirectory(), args);
		monitor.exitWrite();
		return result;
	}

	public IStatus deleteFolder(IPath folderPath)
	{
		IStatus result = execute(GitRepository.ReadWrite.WRITE, "rm", "-rf", folderPath.toOSString()); //$NON-NLS-1$ //$NON-NLS-2$
		if (result == null)
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), "Failed to execute git rm -rf"); //$NON-NLS-1$
		}
		if (!result.isOK())
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), result.getCode(), result.getMessage(), null);
		}
		index().refreshAsync();
		return Status.OK_STATUS;
	}

	public IStatus moveFile(IPath source, IPath dest)
	{
		IStatus result = execute(GitRepository.ReadWrite.WRITE, "mv", source.toOSString(), dest.toOSString()); //$NON-NLS-1$
		if (result == null || !result.isOK())
		{
			return new Status(IStatus.ERROR, GitPlugin.getPluginId(), (result == null) ? 0 : result.getCode(),
					(result == null) ? null : result.getMessage(), null);
		}
		index().refreshAsync();
		return Status.OK_STATUS;
	}

	public IPath relativePath(IResource theResource)
	{
		IPath workingDirectory = workingDirectory();
		IPath resourcePath = theResource.getLocation();
		if (workingDirectory.isPrefixOf(resourcePath))
		{
			resourcePath = resourcePath.makeRelativeTo(workingDirectory);
		}
		// What if we have some trailing slash or something?
		if (resourcePath.isEmpty())
		{
			resourcePath = Path.fromOSString(currentBranch());
		}
		return resourcePath;
	}

	/**
	 * Returns the set of short names for all simple refs that are local or remote branches or tags.
	 * 
	 * @return
	 */
	public Set<String> allSimpleRefs()
	{
		Set<String> allRefs = new HashSet<String>();
		for (GitRevSpecifier revSpec : branches)
		{
			if (!revSpec.isSimpleRef())
			{
				continue;
			}
			GitRef ref = revSpec.simpleRef();
			if (ref == null || ref.type() == null)
			{
				continue;
			}
			allRefs.add(ref.shortName());
		}
		return allRefs;
	}

	void dispose()
	{
		// clean up any listeners/etc!
		if (fileWatcherIds != null)
		{
			for (Integer fileWatcherId : fileWatcherIds)
			{
				try
				{
					FileWatcher.removeWatch(fileWatcherId);
				}
				catch (JNotifyException e)
				{
					GitPlugin.logError(e.getMessage(), e);
				}
			}
		}
		fileWatcherIds = null;
		if (listeners != null)
		{
			synchronized (listeners)
			{
				listeners.clear();
				listeners = null;
			}
		}
		_headRef = null;
		hasChanged = false;
		index = null;
		refs = null;
		branches = null;
	}

	public boolean hasUnresolvedMergeConflicts()
	{
		return index().hasUnresolvedMergeConflicts();
	}

	public List<String> getMergeSHAs()
	{
		List<String> shas = new ArrayList<String>();
		if (!mergeHeadFile().exists())
		{
			return shas;
		}
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader(new FileReader(mergeHeadFile()));
			String sha = null;
			while ((sha = reader.readLine()) != null)
			{
				shas.add(sha);
			}
		}
		catch (Exception e)
		{
			GitPlugin.logError(e.getMessage(), e);
		}
		finally
		{
			if (reader != null)
			{
				try
				{
					reader.close();
				}
				catch (IOException e)
				{
					// ignore
				}
			}
		}
		return shas;
	}

	private File mergeHeadFile()
	{
		return gitFile(MERGE_HEAD_FILENAME);
	}

	private File gitFile(String string)
	{
		return gitDirPath().append(string).toFile();
	}

	private IPath gitDirPath()
	{
		return Path.fromOSString(new File(fileURL).getAbsolutePath());
	}

	public void firePullEvent()
	{
		if (listeners == null || listeners.isEmpty())
		{
			return;
		}
		PullEvent e = new PullEvent(this);
		for (IGitRepositoryListener listener : listeners)
		{
			listener.pulled(e);
		}
	}

	public void firePushEvent()
	{
		if (listeners == null || listeners.isEmpty())
		{
			return;
		}
		PushEvent e = new PushEvent(this);
		for (IGitRepositoryListener listener : listeners)
		{
			listener.pushed(e);
		}
	}

	/**
	 * For use in telling if a given resource is a changed file, or is a folder containing changes underneath it.
	 * 
	 * @param resource
	 * @param changedFiles
	 * @return
	 */
	public boolean resourceOrChildHasChanges(IResource resource)
	{
		return index().resourceOrChildHasChanges(resource);
	}

	/**
	 * Gets the list of changed files that are underneath the given container.
	 * 
	 * @param container
	 * @return
	 */
	public List<ChangedFile> getChangedFilesForContainer(IContainer container)
	{
		return index().getChangedFilesForContainer(container);
	}

	URI getFileURL()
	{
		return fileURL;
	}

	public void addListener(IGitRepositoryListener listener)
	{
		if (listener == null)
		{
			return;
		}

		if (listeners == null)
		{
			listeners = new HashSet<IGitRepositoryListener>(3);
		}
		synchronized (listeners)
		{
			listeners.add(listener);
		}
	}

	public void removeListener(IGitRepositoryListener listener)
	{
		if (listener == null || listeners == null)
		{
			return;
		}
		synchronized (listeners)
		{
			listeners.remove(listener);
		}
	}

	public Set<IResource> getChangedResources()
	{
		return index().getChangedResources();
	}

	public IFile getFileForChangedFile(ChangedFile file)
	{
		return index().getResourceForChangedFile(file);
	}

	/**
	 * Attempts to resolve the ref to it's SHA. takes in something like refs/heads/master or refs/remotes/origin/master
	 * and returns it's SHA. Checks for the refs/* file and failing that looks in packed-refs. If this fails, it will
	 * return the ref's original string (i.e. refs/heads/master)
	 * 
	 * @param ref
	 * @return
	 */
	public String toSHA(GitRef ref)
	{
		File sha1File = gitFile(ref.ref());
		// If the file doesn't exist, it's inside packed-refs!
		try
		{
			if (!sha1File.isFile())
			{
				File packedRefs = gitFile(PACKED_REFS);
				String packedRefContents = IOUtil.read(new FileInputStream(packedRefs));
				// each line is 40 char sha, space, ref name
				int index = packedRefContents.indexOf(ref.ref());
				if (index != -1)
				{
					return new String(packedRefContents.substring(index - 41, index - 1));
				}
			}
			else
			{
				return IOUtil.read(new FileInputStream(sha1File)).trim();
			}
		}
		catch (FileNotFoundException e)
		{
			// ignore
			e.printStackTrace();
		}
		return ref.ref();
	}

	/**
	 * Add the filename to .gitignore
	 * 
	 * @param resource
	 * @return
	 */
	public boolean ignoreResource(IResource resource)
	{
		IPath relativePath = relativePath(resource);
		return ignore(relativePath.toPortableString());
	}

	/**
	 * Adds a pattern to the repo's .gitignore file.
	 * 
	 * @param pattern
	 * @return
	 */
	public boolean ignore(String pattern)
	{
		File gitIgnore = new File(workingDirectory().toFile(), GITIGNORE);
		PrintWriter writer = null;
		try
		{
			// FIXME Don't write duplicate entries!
			writer = new PrintWriter(new FileWriter(gitIgnore, true));
			writer.println(pattern);
			return true;
		}
		catch (IOException e)
		{
			GitPlugin.logError(e.getMessage(), e);
			return false;
		}
		finally
		{
			if (writer != null)
			{
				writer.close();
			}
		}
	}

	/**
	 * Returns the set of branches that are tied to a remote branch that has changes.
	 * 
	 * @return
	 */
	public synchronized Set<String> getOutOfDateBranches()
	{
		// TODO Does this report properly if the local branch has commits and there's no changes remotely?
		// TODO What about when we have commits locally and remotely?

		// Check to see if user has disabled performing remote fetches for pull indicator calculations.
		boolean performFetches = Platform.getPreferencesService().getBoolean(GitPlugin.getPluginId(),
				IPreferenceConstants.GIT_CALCULATE_PULL_INDICATOR, false, null);
		if (!performFetches)
		{
			return Collections.emptySet();
		}

		// First limit it down to a map of local branches to their matching remotes.
		Map<String, GitRef> localToRemote = new HashMap<String, GitRef>();
		for (String branchName : localBranches())
		{
			GitRef remote = matchingRemoteBranch(branchName);
			if (remote == null)
			{
				continue;
			}
			localToRemote.put(branchName, remote);
		}

		// If there are no branches with remote bracnhes, just return empty set
		if (localToRemote.isEmpty())
		{
			return Collections.emptySet();
		}

		// First pass, compare the local branch ref to the local copy of the remote ref
		// Have we already fetched, it has changes, but hasn't been merged?
		Set<String> toPull = new HashSet<String>();
		Iterator<Map.Entry<String, GitRef>> iter = localToRemote.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<String, GitRef> entry = iter.next();

			String[] commits = index().commitsBetween(GitRef.REFS_HEADS + entry.getKey(), entry.getValue().ref());
			if (commits != null && commits.length > 0)
			{
				// No need to hit network, we can see that there's changes on local copy of remote branch. fetched, but
				// not yet merged
				toPull.add(entry.getKey());
				iter.remove();
				continue;
			}
		}

		// Can we stop early and avoid hitting ls-remote?
		if (localToRemote.isEmpty())
		{
			return toPull;
		}

		// OK, do the heavy lifting, get the unique set of remotes from the left over branches.
		// For each remote, do a ls-remote and store the output
		Map<String, String> remoteNameToOutput = new HashMap<String, String>();
		for (Map.Entry<String, GitRef> entry : localToRemote.entrySet())
		{
			String remote = entry.getValue().getRemoteName();
			if (remoteNameToOutput.containsKey(remote))
			{
				// Only do ls-remote once per remote
				continue;
			}

			IStatus result = executeWithPromptHandling(GitRepository.ReadWrite.READ, "ls-remote", "--heads", remote); //$NON-NLS-1$ //$NON-NLS-2$
			if (result == null || !result.isOK())
			{
				// Failed to execute properly
				if (result != null)
				{
					GitPlugin.getDefault().getLog().log(result);
				}
				// Store empty output to avoid hitting this remote again
				remoteNameToOutput.put(remote, ""); //$NON-NLS-1$
			}
			else
			{
				remoteNameToOutput.put(remote, result.getMessage());
			}
		}

		// Now process the outputs, matching up the remote refs to local branches and comparing their SHAs
		// TODO Move this pattern up to be a field that gets lazily compiled?
		Pattern p = Pattern.compile("^([0-9a-fA-F]{40})\\s+(refs/heads/.+)$", Pattern.MULTILINE); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : remoteNameToOutput.entrySet())
		{
			String output = entry.getValue();
			if (output.length() == 0)
			{
				continue;
			}

			Matcher m = p.matcher(output);
			while (m.find())
			{
				String remoteSHA = m.group(1);
				String ref = m.group(2);

				// Find the local branch for this remote ref, compare SHAs
				String localBranchName = null;
				for (Map.Entry<String, GitRef> localToRemoteRefEntry : localToRemote.entrySet())
				{
					// Do both remote match and the branch name match?
					if (entry.getKey().equals(localToRemoteRefEntry.getValue().getRemoteName())
							&& ref.equals(GitRef.REFS_HEADS + localToRemoteRefEntry.getValue().getRemoteBranchName()))
					{
						localBranchName = localToRemoteRefEntry.getKey();
						break;
					}
				}
				if (localBranchName != null)
				{
					String localSHA = toSHA(GitRef.refFromString(GitRef.REFS_HEADS + localBranchName));
					if (!localSHA.equals(remoteSHA))
					{
						// SHAs don't match, so there are changes. Big question is where did they occur? Do we need to
						// check our local copy of the remote ref too?
						toPull.add(localBranchName);
						// This local branch is handled, remove it from eligible list...
						localToRemote.remove(localBranchName);
						if (localToRemote.isEmpty()) // no more local branches we need to handle?, break out of loop!
						{
							break;
						}
					}
				}
			}
		}

		return toPull;
	}

	/**
	 * Not to be used by callers! This is for exiting write lock when we run git commands like push/pull in console!
	 */
	public void exitWriteProcess()
	{
		monitor.exitWrite();
	}

	/**
	 * Not to be used by callers! This is for entering write lock when we run git commands like push/pull in console!
	 */
	public void enterWriteProcess()
	{
		monitor.enterWrite();
	}

	/**
	 * Not to be used by callers! This is for entering read lock when we run git commands outside this class!
	 */
	void enterRead()
	{
		monitor.enterRead();
	}

	/**
	 * Not to be used by callers! This is for exiting read lock when we run git commands outside this class!
	 */
	void exitRead()
	{
		monitor.exitRead();
	}

	public Set<String> getGithubURLs()
	{
		Set<String> githubURLs = new HashSet<String>();
		// Check the remote urls for github and use that to determine URL we need!
		for (String remoteURL : remoteURLs())
		{
			if (!remoteURL.contains(GITHUB_COM))
			{
				continue;
			}
			String remaining = remoteURL.substring(remoteURL.indexOf(GITHUB_COM) + 10);
			if (remaining.startsWith("/") || remaining.startsWith(":")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				remaining = remaining.substring(1);
			}
			if (remaining.endsWith(GitRepository.GIT_DIR))
			{
				remaining = remaining.substring(0, remaining.length() - 4);
			}
			int split = remaining.indexOf("/"); //$NON-NLS-1$
			String userName = remaining.substring(0, split);
			String repoName = remaining.substring(split + 1);
			githubURLs.add(MessageFormat.format("https://github.com/{0}/{1}", userName, repoName)); //$NON-NLS-1$
		}
		return githubURLs;
	}

	/**
	 * If the user has run something like a git merge --squash, it pre-populates the commit message for you with the
	 * concat of the squashed commits. We should sniff for this and use it when available.
	 * 
	 * @return
	 */
	public String getPrepopulatedCommitMessage()
	{
		try
		{
			File squashMsg = gitFile(SQUASH_MSG);
			if (squashMsg.exists())
			{
				return IOUtil.read(new FileInputStream(squashMsg));
			}
		}
		catch (FileNotFoundException e)
		{
			// ignore
		}
		return StringUtil.EMPTY;
	}
}
