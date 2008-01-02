/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation and others.
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
 * $Id: WorkspaceSynchronizerTest.java,v 1.9 2008/01/02 16:12:16 cdamus Exp $
 */
package org.eclipse.emf.workspace.util.tests;

import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.validation.marker.MarkerUtil;
import org.eclipse.emf.workspace.tests.AbstractTest;
import org.eclipse.emf.workspace.util.WorkspaceSynchronizer;

/**
 * Tests the {@link WorkspaceSynchronizer} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class WorkspaceSynchronizerTest extends AbstractTest {
	
	private WorkspaceSynchronizer synch;
	private TestDelegate delegate;
	
	public WorkspaceSynchronizerTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(WorkspaceSynchronizerTest.class, "Workspace Synchronizer Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests the static getFile() utility method.
	 */
	public void test_getFile() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		
		URI uri = testResource.getURI();
		assertEquals(file.getName(), uri.segment(uri.segmentCount() - 1));
	}

	/**
	 * Tests the static getUnderlyingFile() utility method.
	 */
	public void test_getUnderlyingFile_163291() {
		Resource archiveResource = new ResourceImpl();
		archiveResource.setURI(URI.createURI("archive:platform:/resource" + RESOURCE_NAME + "!/foo"));
		IFile file = WorkspaceSynchronizer.getUnderlyingFile(archiveResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		assertEquals(file, this.file);

		archiveResource = new ResourceImpl();
		archiveResource.setURI(URI.createURI("archive:platform:/resource" + RESOURCE_NAME + "!/foo.zip!/goo"));
		file = WorkspaceSynchronizer.getUnderlyingFile(archiveResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		assertEquals(file, this.file);

		archiveResource = new ResourceImpl();
		archiveResource.setURI(URI.createURI("archive:archive:platform:/resource" + RESOURCE_NAME + "!/foo.zip!/goo"));
		file = WorkspaceSynchronizer.getUnderlyingFile(archiveResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		assertEquals(file, this.file);
	}
	
	/**
	 * Tests the getFile() with file: URI.
	 */
	public void test_getFile_fileURI_156772() {
		String path = ResourcesPlugin.getWorkspace().getRoot().getLocation().append(RESOURCE_NAME).toString();
		
		testResource.setURI(URI.createFileURI(path));
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		
		URI uri = testResource.getURI();
		assertEquals(file.getName(), uri.segment(uri.segmentCount() - 1));
	}
	
	/**
	 * Tests the getFile() with URI that can be normalized to a platform URI.
	 */
	public void test_getFile_normalization_156772() {
		testResource.getResourceSet().getURIConverter().getURIMap().put(
				URI.createURI("pathmap://FOO"), //$NON-NLS-1$
				testResource.getURI().trimSegments(1));
				
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		assertNotNull(file);
		assertTrue(file.exists());
		
		URI uri = testResource.getURI();
		assertEquals(file.getName(), uri.segment(uri.segmentCount() - 1));
	}
	
	/**
	 * Tests that resource deletion is correctly reported to the delegate to
	 * handle.
	 */
	public void test_deletion() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		try {
			synchronized (delegate) {
				file.delete(true, null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}
		
		assertTrue(delegate.deletedResources.contains(testResource));
		assertFalse(delegate.changedResources.contains(testResource));
		assertFalse(delegate.movedResources.containsKey(testResource));
	}
	
	/**
	 * Tests that resource change is correctly reported to the delegate to
	 * handle.
	 */
	public void test_change() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		try {
			synchronized (delegate) {
				file.touch(null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}
		
		assertTrue(delegate.changedResources.contains(testResource));
		assertFalse(delegate.deletedResources.contains(testResource));
		assertFalse(delegate.movedResources.containsKey(testResource));
	}
	
	/**
	 * Tests that resource move is correctly reported to the delegate to
	 * handle (this is actually a rename scenario).
	 */
	public void test_move() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		IPath newPath = file.getFullPath().removeLastSegments(1).append(
				"moveDestination.extlibrary"); //$NON-NLS-1$
		
		try {
			synchronized (delegate) {
				file.move(newPath, true, null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}
		
		assertFalse(delegate.changedResources.contains(testResource));
		assertFalse(delegate.deletedResources.contains(testResource));
		assertTrue(delegate.movedResources.containsKey(testResource));
		assertEquals(
				URI.createPlatformResourceURI(newPath.toString(), true),
				delegate.movedResources.get(testResource));
	}
	
	/**
	 * Tests that multiple changes in the same editing domain are reported
	 * correctly to the delegate.
	 */
	public void test_multipleChanges() {
		final IFile file = WorkspaceSynchronizer.getFile(testResource);
		final IFile[] copies = new IFile[2];
		
		final IPath copy1 = file.getFullPath().removeLastSegments(1).append(
				"copy1.extlibrary"); //$NON-NLS-1$
		final IPath copy2 = file.getFullPath().removeLastSegments(1).append(
				"copy2.extlibrary"); //$NON-NLS-1$
		final IPath newPath = file.getFullPath().removeLastSegments(1).append(
				"moveDestination.extlibrary"); //$NON-NLS-1$
		
		Job job = new WorkspaceJob("Modify Workspace") { //$NON-NLS-1$
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor)
					throws CoreException {
				// make two copies
				file.copy(copy1, true, null);
				file.copy(copy2, true, null);
				
				// store the files
				copies[0] = file.getWorkspace().getRoot().getFile(copy1);
				copies[1] = file.getWorkspace().getRoot().getFile(copy2);
				
				return Status.OK_STATUS;
			}};
		job.schedule();
		
		try {
			job.join();
		} catch (InterruptedException e) {
			fail(e);
		}
		
		// load the copies
		Resource testResource2 = domain.getResourceSet().getResource(
				URI.createPlatformResourceURI(copy1.toString(), true), true);
		Resource testResource3 = domain.getResourceSet().getResource(
				URI.createPlatformResourceURI(copy2.toString(), true), true);
		
		assertNotNull(testResource2);
		assertTrue(testResource2.isLoaded());
		assertNotNull(testResource3);
		assertTrue(testResource3.isLoaded());
		
		try {
			// make the workspace changes in a single job so that all deltas
			//    are fired in one batch
			job = new WorkspaceJob("Modify Workspace") { //$NON-NLS-1$
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor)
						throws CoreException {
					// delete one file
					file.delete(true, null);
					
					// change another's contents
					copies[0].touch(null);
					
					// and move a third
					copies[1].move(newPath, true, null);
					
					return Status.OK_STATUS;
				}};
			job.schedule();
			job.join();
		} catch (Exception e) {
			fail(e);
		}
		
		waitForWorkspaceChanges();
		
		assertTrue(delegate.deletedResources.contains(testResource));
		assertTrue(delegate.changedResources.contains(testResource2));
		assertTrue(delegate.movedResources.containsKey(testResource3));
		assertEquals(
				URI.createPlatformResourceURI(newPath.toString(), true),
				delegate.movedResources.get(testResource3));
	}
	
	/**
	 * Tests the default response to resource deletion.
	 */
	public void test_defaultDeleteBehaviour() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		delegate.defaultBehaviour = true;
		
		assertTrue(testResource.isLoaded());
		
		try {
			synchronized (delegate) {
				file.delete(true, null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}

		waitForWorkspaceChanges();
		
		assertFalse(testResource.isLoaded());
	}
	
	/**
	 * Tests the default response to resource change.  Note that the default
	 * test resource URI does not require any URI-encoding.
	 */
	public void test_defaultChangeBehaviour() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		
		delegate.defaultBehaviour = true;
		
		assertTrue(testResource.isLoaded());
		
		try {
			synchronized (delegate) {
				file.touch(null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}

		waitForWorkspaceChanges();
		
		// check that the resource is loaded but has different contents than
		//    it had before
		assertTrue(testResource.isLoaded());
		assertFalse(testResource.getContents().contains(root));
	}
	
	/**
	 * Tests the default response to a resource move.
	 */
	public void test_defaultMoveBehaviour() {
		IFile file = WorkspaceSynchronizer.getFile(testResource);
		IPath newPath = file.getFullPath().removeLastSegments(1).append(
				"moveDestination.extlibrary"); //$NON-NLS-1$
		
		delegate.defaultBehaviour = true;
		
		assertTrue(testResource.isLoaded());
		
		try {
			synchronized (delegate) {
				file.move(newPath, true, null);
				delegate.wait();
			}
		} catch (Exception e) {
			fail(e);
		}

		waitForWorkspaceChanges();
		
		assertFalse(testResource.isLoaded());
	}
	
	/**
	 * Checks that URIs are decoded when constructing file paths.
	 */
	public void test_getFileWithEncodedURI_128315() {
		final String filePath = "/My Project/some dir/file.foo"; //$NON-NLS-1$
		final String encoded = "platform:/resource/My%20Project/some%20dir/file.foo"; //$NON-NLS-1$
		
		URI uri = URI.createPlatformResourceURI(filePath, true);
		
		// URI does encodes itself
		assertEquals(encoded, uri.toString());
		
		Resource res = new ResourceImpl(uri);
		
		IFile file = WorkspaceSynchronizer.getFile(res);
		
		assertEquals(filePath, file.getFullPath().toString());
	}
	
	/**
	 * Tests synchronization of an in-memory <code>Resource</code> with a change
	 * in the workspace <code>IResource</code> when the <code>Resource</code>'s
	 * URI is not encoded but should have been.
	 */
	public void test_synchResourceWithUnencodedURI_197291() {
	    // don't encode the URI
	    Resource res = createTestResource(TEST_RESOURCE_NAME,
            "name with spaces.extlibrary", false); //$NON-NLS-1$
	    root = (Library) res.getContents().get(0);
	    
        IFile file = WorkspaceSynchronizer.getFile(res);
        
        delegate.defaultBehaviour = true;
        
        assertTrue(testResource.isLoaded());
        
        try {
            synchronized (delegate) {
                file.touch(null);
                delegate.wait(100000L);
            }
        } catch (Exception e) {
            fail(e);
        }

        waitForWorkspaceChanges();
        
        // check that the resource is loaded but has different contents than
        //    it had before
        assertTrue(res.isLoaded());
        assertFalse(res.getContents().contains(root));
	}
    
    /**
     * Tests synchronization of an in-memory <code>Resource</code> with a change
     * in the workspace <code>IResource</code> when the <code>Resource</code>'s
     * URI is encoded (and needed to be).
     */
    public void test_synchResourceWithEncodedURI_197291() {
        // *do* encode the URI
        Resource res = createTestResource(TEST_RESOURCE_NAME,
            "name with spaces.extlibrary", true); //$NON-NLS-1$
        root = (Library) res.getContents().get(0);
        
        IFile file = WorkspaceSynchronizer.getFile(res);
        
        delegate.defaultBehaviour = true;
        
        assertTrue(testResource.isLoaded());
        
        try {
            synchronized (delegate) {
                file.touch(null);
                delegate.wait(10000L);
            }
        } catch (Exception e) {
            fail(e);
        }

        waitForWorkspaceChanges();
        
        // check that the resource is loaded but has different contents than
        //    it had before
        assertTrue(res.isLoaded());
        assertFalse(res.getContents().contains(root));
    }
    
    /**
     * Tests synchronization of an in-memory <code>Resource</code> with a change
     * in the workspace <code>IResource</code> when the <code>Resource</code>'s
     * URI is not encoded but should have been.
     */
    public void test_synchMovedResourceWithUnencodedURI_197291() {
        // don't encode the URI
        Resource res = createTestResource(TEST_RESOURCE_NAME,
            "name with spaces.extlibrary", false); //$NON-NLS-1$
        root = (Library) res.getContents().get(0);
        
        IFile file = WorkspaceSynchronizer.getFile(res);
        
        IPath path = file.getFullPath().removeLastSegments(1).append(
            "new name.extlibrary"); //$NON-NLS-1$
        Resource newRes = domain.createResource(
            URI.createPlatformResourceURI(path.toString(), false).toString());
        
        delegate.defaultBehaviour = true;
        
        assertTrue(testResource.isLoaded());
        
        try {
            synchronized (delegate) {
                file.move(path, true, null);
                delegate.wait(100000L);
            }
        } catch (Exception e) {
            fail(e);
        }

        waitForWorkspaceChanges();
        
        assertFalse(delegate.changedResources.contains(res));
        assertFalse(delegate.deletedResources.contains(res));
        assertTrue(delegate.movedResources.containsKey(res));
        assertEquals(newRes.getURI(), delegate.movedResources.get(res));
    }
    
    /**
     * Tests synchronization of an in-memory <code>Resource</code> with a change
     * in the workspace <code>IResource</code> when the <code>Resource</code>'s
     * URI is encoded (and needed to be).
     */
    public void test_synchMoveResourceWithEncodedURI_197291() {
        // do encode the URI
        Resource res = createTestResource(TEST_RESOURCE_NAME,
            "name with spaces.extlibrary", false); //$NON-NLS-1$
        root = (Library) res.getContents().get(0);
        
        IFile file = WorkspaceSynchronizer.getFile(res);
        
        IPath path = file.getFullPath().removeLastSegments(1).append(
            "new name.extlibrary"); //$NON-NLS-1$
        Resource newRes = domain.createResource(
            URI.createPlatformResourceURI(path.toString(), true).toString());
        
        delegate.defaultBehaviour = true;
        
        assertTrue(testResource.isLoaded());
        
        try {
            synchronized (delegate) {
                file.move(path, true, null);
                delegate.wait(100000L);
            }
        } catch (Exception e) {
            fail(e);
        }

        waitForWorkspaceChanges();
        
        assertFalse(delegate.changedResources.contains(res));
        assertFalse(delegate.deletedResources.contains(res));
        assertTrue(delegate.movedResources.containsKey(res));
        assertEquals(newRes.getURI(), delegate.movedResources.get(res));
    }
    
    /**
     * Tests the response to resource deletion when the deleted resource also
     * had markers.
     */
    public void test_resourceDeletedThatHadMarkers_207306() {
        IFile file = WorkspaceSynchronizer.getFile(testResource);
        
        try {
            IMarker marker = file.createMarker(MarkerUtil.VALIDATION_MARKER_TYPE);
            marker.setAttribute(MarkerUtil.RULE_ATTRIBUTE, "foo"); //$NON-NLS-1$
        } catch (CoreException e) {
            fail(e);
        }
        
        delegate.defaultBehaviour = true;
        
        assertTrue(testResource.isLoaded());
        
        try {
            synchronized (delegate) {
                file.delete(true, null);
                delegate.wait(20000);
            }
        } catch (Exception e) {
            fail(e);
        }

        waitForWorkspaceChanges();
        
        assertFalse(testResource.isLoaded());
    }
	
	//
	// Fixture methods
	//
	
	@Override
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		delegate = new TestDelegate();
		synch = new WorkspaceSynchronizer(domain, delegate);
	}
	
	@Override
	protected void doTearDown()
		throws Exception {
		
		synch.dispose();
		synch = null;
		delegate = null;
		
		super.doTearDown();
	}
	
	/**
	 * Waits for any pending workspace changes to finish by scheduling a job
	 * on the workspace root and waiting for it to finish.
	 */
	void waitForWorkspaceChanges() {
		final Object lock = new Object();
		
		Job job = new Job("Wait Job") { //$NON-NLS-1$
			{
				setRule(ResourcesPlugin.getWorkspace().getRoot());
				setSystem(true);
			}
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				synchronized (lock) {
					lock.notify();
				}
				
				return Status.OK_STATUS;
			}};
			
		synchronized (lock) {
			job.schedule();
			
			try {
				lock.wait();
			} catch (InterruptedException e) {
				fail(e);
			}
		}
	}
	
	/**
	 * Delegate implementation for testing, basically tracking the call-backs
	 * received from the workspace synchronizer.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	static class TestDelegate implements WorkspaceSynchronizer.Delegate {
		final List<Resource> deletedResources = new java.util.ArrayList<Resource>();
		final Map<Resource, URI> movedResources = new java.util.LinkedHashMap<Resource, URI>();
		final List<Resource> changedResources = new java.util.ArrayList<Resource>();
		
		boolean defaultBehaviour = false;
		
		public synchronized boolean handleResourceDeleted(Resource resource) {
			deletedResources.add(resource);
			
			notify();
			
			return !defaultBehaviour;
		}

		public synchronized boolean handleResourceMoved(Resource resource, URI newURI) {
			movedResources.put(resource, newURI);
			
			notify();
			
			return !defaultBehaviour;
		}

		public synchronized boolean handleResourceChanged(Resource resource) {
			changedResources.add(resource);
			
			notify();
			
			return !defaultBehaviour;
		}
		
		public void dispose() {
			deletedResources.clear();
			movedResources.clear();
			changedResources.clear();
		}
	}
}
