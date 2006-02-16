/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: WorkspaceSynchronizer.java,v 1.3 2006/02/16 22:26:47 cdamus Exp $
 */
package org.eclipse.emf.workspace.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.Tracing;
import org.eclipse.emf.workspace.internal.l10n.Messages;

/**
 * A utility object that listens to workspace resource changes to synchronize
 * the state of an EMF resource set with the workspace.
 * <p>
 * The default behaviour
 * on workspace resource deletions is to unload the corresponding EMF resource.
 * The default behaviour on resource changes is to unload and reload the
 * corresponding EMF resource, unless the resource path has changed (by move
 * or rename), in which case it is simply unloaded.
 * </p>
 * <p>
 * To customize the behaviour of the synchronizer, initialize it with a
 * {@link WorkspaceSynchronizer.Delegate delegate} that provides the required
 * behaviour.  For example, it might be more user-friendly to prompt the user
 * before taking drastic measures.
 * </p>
 * <p>
 * Whether implemented by a delegate or not, the synchronization algorithm is
 * invoked asynchronously (as a job) and in a read-only transaction on the
 * synchronizer's editing domain.  This ensures timely completion of the
 * workspace's event dispatching and exclusive access to the resource set
 * according to the transaction protocol.  Also, the job is scheduled on the
 * workspace rule, so that the delegate call-backs are free to read or modify
 * any resources that they may need.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 */
public final class WorkspaceSynchronizer {
	private final TransactionalEditingDomain domain;
	private Delegate delegate;
	
	// we employ a copy-on-write strategy on this collection for thread safety
	private static Collection synchronizers = new java.util.ArrayList();
	
	// we use a single listener to serve all synchronizers.
	private static IResourceChangeListener workspaceListener =
		new WorkspaceListener();

	// the default synchronization strategies
	static Delegate defaultDelegate = new DefaultDelegate();
	
	/**
	 * Initializes me with the editing domain for which I synchronize resources,
	 * using the default change-handling behaviour.
	 * <p>
	 * I immediately start listening for workspace resource changes.
	 * </p>
	 * 
	 * @param domain my domain (must not be <code>null</code>)
	 */
	public WorkspaceSynchronizer(TransactionalEditingDomain domain) {
		this(domain, null);
	}
	
	/**
	 * Initializes me with the editing domain for which I synchronize resources,
	 * using the specified delegate to handle resource changes.
	 * <p>
	 * I immediately start listening for workspace resource changes.
	 * </p>
	 * 
	 * @param domain my domain (must not be <code>null</code>)
	 * @param delegate the delegate that handles my resource changes, or
	 *     <code>null</code> to get the default behaviour
	 */
	public WorkspaceSynchronizer(TransactionalEditingDomain domain, Delegate delegate) {
		if (domain == null) {
			throw new IllegalArgumentException("null domain"); //$NON-NLS-1$
		}
		
		if (delegate == null) {
			delegate = defaultDelegate;
		}
		
		this.domain = domain;
		this.delegate = delegate;
		
		startListening(this);
	}
	
	/**
	 * Queries the editing domain whose resources I synchronize with the
	 * workspace.
	 * 
	 * @return my editing domain
	 */
	public TransactionalEditingDomain getEditingDomain() {
		return domain;
	}
	
	/**
	 * Obtains the delegate that handles resource changes.
	 * 
	 * @return my delegate
	 */
	Delegate getDelegate() {
		return delegate;
	}
	
	/**
	 * Disposes me, in particular disconnecting me from the workspace so that
	 * I no longer respond to resource change events.
	 */
	public void dispose() {
		stopListening(this);
		delegate.dispose();
		delegate = null;
	}
	
	/**
	 * Processes a resource delta to determine whether it corresponds to a
	 * resource in my editing domain and, if so, how to handle removal or
	 * change of that resource.
	 * 
	 * @param delta the resource change
	 * @param synchRequests accumulates synch requests for the deltas
	 */
	void processDelta(IResourceDelta delta, List synchRequests) {
		Resource resource = getEditingDomain().getResourceSet().getResource(
				URI.createPlatformResourceURI(delta.getFullPath().toString()), false);
		
		if ((resource != null) && resource.isLoaded()) {
			switch (delta.getKind()) {
			case IResourceDelta.REMOVED:
				if ((delta.getFlags() & IResourceDelta.MOVED_TO) != 0) {
					synchRequests.add(new MovedSynchRequest(
							this,
							resource,
							URI.createPlatformResourceURI(
									delta.getMovedToPath().toString())));
				} else {
					synchRequests.add(new DeletedSynchRequest(this, resource));
				}
				
				break;
			case IResourceDelta.CHANGED:
				synchRequests.add(new ChangedSynchRequest(this, resource));
				break;
			}
		}
	}
	
	/**
	 * Obtains the workspace file corresponding to the specified resource, if
	 * it has a platform-resource URI.  Note that the resulting file, if not
	 * <code>null</code>, may nonetheless not actually exist (as the file is
	 * just a handle).
	 * 
	 * @param resource an EMF resource
	 * 
	 * @return the corresponding workspace file, or <code>null</code> if the
	 *    resource's URI is not a platform-resource URI
	 */
	public static IFile getFile(Resource resource) {
		IFile result = null;
		URI uri = resource.getURI();
		
		if ("platform".equals(uri.scheme()) && (uri.segmentCount() > 2)) { //$NON-NLS-1$
			if ("resource".equals(uri.segment(0))) { //$NON-NLS-1$
				IPath path = new Path(URI.decode(uri.path())).removeFirstSegments(1);
				
				result = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			}
		}
		
		return result;
	}
	
	/**
	 * Starts a synchronizer listening to resource change events.
	 * 
	 * @param synchronizer the synchronizer to start
	 */
	static void startListening(WorkspaceSynchronizer synchronizer) {
		// copy-on-write for thread safety
		synchronized (synchronizers) {
			Collection newList = new java.util.ArrayList(synchronizers.size() + 1);
			newList.addAll(synchronizers);
			newList.add(synchronizer);
			synchronizers = newList;
			
			// ensure that we are listening to the workspace
			ResourcesPlugin.getWorkspace().addResourceChangeListener(
					workspaceListener,
					IResourceChangeEvent.POST_CHANGE);
		}
	}

	/**
	 * Stops a synchronizer listening to resource change events.
	 * 
	 * @param synchronizer the synchronizer to stop
	 */
	static void stopListening(WorkspaceSynchronizer synchronizer) {
		// copy-on-write for thread safety
		synchronized (synchronizers) {
			Collection newList = new java.util.ArrayList(synchronizers);
			newList.remove(synchronizer);
			synchronizers = newList;
			
			if (synchronizers.isEmpty()) {
				// stop listening to the workspace
				ResourcesPlugin.getWorkspace().removeResourceChangeListener(
						workspaceListener);
			}
		}
	}
	
	/**
	 * Obtains the synchronizers that need to process a resource change event.
	 * 
	 * @return the currently active synchronizers
	 */
	static Collection getSynchronizers() {
		// does not need synchronization because we copy on write
		return synchronizers;
	}
	
	/**
	 * Call-back interface for an object to which a {@link WorkspaceSynchronizer}
	 * delegates the algorithms for handling different kinds of resource
	 * changes.
	 * <p>
	 * Every call-back is invoked asynchronously in a read-only transaction on
	 * the synchronizer's editing domain.  Any model changes that the
	 * receiver wishes to make must be scheduled asynchronously, although
	 * workspace changes are permitted as the calling thread has the
	 * workspace lock.  The call-backs are not actually required to handle the
	 * resource change; they can defer to the default behaviour.
	 * </p>
	 * 
	 * @author Christian W. Damus (cdamus)
	 */
	public static interface Delegate {
		/**
		 * Optionally handles the deletion of the physical workspace resource
		 * behind the specified EMF resource.
		 * 
		 * @param resource a resource whose storage has been deleted
		 * 
		 * @return <code>true</code> if I handled the resource deletion;
		 *    <code>false</code> to defer to the workspace synchronizer's
		 *    default algorithm
		 */
		boolean handleResourceDeleted(Resource resource);
		
		/**
		 * Optionally handles the move of the physical workspace resource
		 * behind the specified EMF resource.  Both in-place renames of a
		 * resource and relocations of a resource to another container are
		 * considered as moves.
		 * 
		 * @param resource a resource whose storage has been moved
		 * @param newURI the new URI of the moved resource
		 * 
		 * @return <code>true</code> if I handled the resource deletion;
		 *    <code>false</code> to defer to the workspace synchronizer's
		 *    default algorithm
		 */
		boolean handleResourceMoved(Resource resource, URI newURI);
		
		/**
		 * Optionally handles a change to the physical workspace resource
		 * behind the specified EMF resource.
		 * 
		 * @param resource a resource whose storage has been changed
		 * 
		 * @return <code>true</code> if I handled the resource change;
		 *    <code>false</code> to defer to the workspace synchronizer's
		 *    default algorithm
		 */
		boolean handleResourceChanged(Resource resource);
		
		/**
		 * Disposes me.  This is called by the synchronizer when it is disposed.
		 */
		void dispose();
	}
	
	/**
	 * The single shared workspace listener that passes workspace changes
	 * along to the currently active synchronizers.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private static class WorkspaceListener implements IResourceChangeListener {
		public void resourceChanged(IResourceChangeEvent event) {
			IResourceDelta delta = event.getDelta();
			
			try {
				final List synchRequests = new java.util.ArrayList();
				
				delta.accept(new IResourceDeltaVisitor() {
					public boolean visit(IResourceDelta delta) {
						if ((delta.getFlags() != IResourceDelta.MARKERS) &&
						      (delta.getResource().getType() == IResource.FILE)) {
							switch (delta.getKind()) {
							case IResourceDelta.CHANGED:
							case IResourceDelta.REMOVED:
								processDelta(delta, synchRequests);
								break;
							}
						}

						return true;
					}});
				
				if (!synchRequests.isEmpty()) {
					new ResourceSynchJob(synchRequests).schedule();
				}
			} catch (CoreException e) {
				Tracing.catching(WorkspaceListener.class, "resourceChanged", e); //$NON-NLS-1$
				EMFWorkspacePlugin.INSTANCE.log(e);
			}
		}
		
		/**
		 * Passes the delta to all available synchronizers, to process it.
		 * 
		 * @param delta the delta to process
		 * @param synchRequests accumulates synch requests for the deltas
		 */
		private void processDelta(IResourceDelta delta, List synchRequests) {
			for (Iterator iter = getSynchronizers().iterator(); iter.hasNext();) {
				((WorkspaceSynchronizer) iter.next()).processDelta(
						delta, synchRequests);
			}
		}
	}
	
	/**
	 * The default algorithms for handling workspace resource changes.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private static class DefaultDelegate implements Delegate {

		public boolean handleResourceDeleted(Resource resource) {
			resource.unload();
			return true;
		}

		public boolean handleResourceMoved(Resource resource, URI newURI) {
			resource.unload();
			return true;
		}

		public boolean handleResourceChanged(Resource resource) {
			resource.unload();
			try {
				resource.load(resource.getResourceSet().getLoadOptions());
			} catch (IOException e) {
				Tracing.catching(DefaultDelegate.class,
						"handleResourceChanged", e); //$NON-NLS-1$
				EMFWorkspacePlugin.INSTANCE.log(e);
			}
			
			return true;
		}
		
		public void dispose() {
			// nothing to dispose (especially as I am shared)
		}
	}
	
	/**
	 * A job that runs under the workspace scheduling rule to process one or
	 * more resource synchronization requests.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	private static class ResourceSynchJob extends WorkspaceJob {
		private final List synchRequests;
		
		/**
		 * Initializes me with the list of resources changes that I am to
		 * process.
		 * 
		 * @param synchRequests the resource synchronization requests
		 */
		ResourceSynchJob(List synchRequests) {
			super(Messages.synchJobName);
			
			this.synchRequests = synchRequests;
			
			setRule(ResourcesPlugin.getWorkspace().getRoot());
		}
		
		/**
		 * Processes my queued resource synchronization requests.
		 */
		public IStatus runInWorkspace(IProgressMonitor monitor) {
			try {
				for (Iterator iter = synchRequests.iterator(); iter.hasNext();) {
					((SynchRequest) iter.next()).perform();
				}
			} catch (InterruptedException e) {
				Tracing.catching(ResourceSynchJob.class, "run", e); //$NON-NLS-1$
				return Status.CANCEL_STATUS;
			}
			
			return Status.OK_STATUS;
		}
	}
}
