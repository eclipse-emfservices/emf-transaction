/**
 * <copyright>
 *
 * Copyright (c) 2006, 2008 IBM Corporation and others.
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
 * $Id: EditingDomainTest.java,v 1.5 2008/05/06 15:05:14 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.io.ByteArrayInputStream;
import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Tests some basic editing domain life-cycle API.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EditingDomainTest extends AbstractTest {
	
	public EditingDomainTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(EditingDomainTest.class, "Editing Domain Life-Cycle Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that the unmapping of the resourceset-domain link works as expected
	 * and that it is performed when disposing the editing domain.
	 */
	public void test_factoryUnmapResourceSet_161168() {
//		ReferenceQueue q = new ReferenceQueue();
		
		TransactionalEditingDomain domain =
			TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		ResourceSet rset = domain.getResourceSet();
		
//		WeakReference ref = new WeakReference(domain, q);
		
		// check initial conditions
		assertSame(domain, TransactionUtil.getEditingDomain(rset));
		
		// dispose and forget the editing domain
		domain.dispose();
		domain = null;
		
		// verify that the resource set has forgotten its editing domain
		assertNull(TransactionUtil.getEditingDomain(rset));

// TODO: Why does this not work in the build but it does in the dev environment?
//		runGC();
//		
//		// verify that the domain was reclaimed
//		assertSame(ref, q.poll());
	}
    
    /**
     * Tests the support for read-only resources in the workspace.
     */
    public void ignore_test_readOnlyResourceMap_workspace_bug156428() {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        
        final IProject proj = ws.getRoot().getProject("read_only_test"); //$NON-NLS-1$
        
        addTearDownAction(new Runnable() {
            public void run() {
                delete(proj);
            }});
        
        try {
            proj.create(null);
            proj.open(null);
        } catch (Exception e) {
            fail("Failed to create project: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        IFile file = proj.getFile("testResource.xmi"); //$NON-NLS-1$
        
        // a resource that doesn't exist should be writable
        Resource res = domain.getResourceSet().createResource(
            URI.createPlatformResourceURI(file.getFullPath().toString(), true));
        assertFalse(domain.isReadOnly(res));
        
        domain.getResourceSet().getResources().remove(res);
        
        try {
            file.create(new ByteArrayInputStream(new byte[0]), false, null);
        } catch (Exception e) {
            fail("Failed to create file: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        // a resource that does exist and is writable should be writable
        res = domain.getResourceSet().createResource(
            URI.createPlatformResourceURI(file.getFullPath().toString(), true));
        assertFalse(domain.isReadOnly(res));

        domain.getResourceSet().getResources().remove(res);
        
        ResourceAttributes attribs = new ResourceAttributes();
        attribs.setReadOnly(true);
        try {
            file.setResourceAttributes(attribs);
        } catch (Exception e) {
            fail("Failed to set file read-only: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        // a resource that does exist and is not writable should be read-only
        res = domain.getResourceSet().createResource(
            URI.createPlatformResourceURI(file.getFullPath().toString(), true));
        assertTrue(domain.isReadOnly(res));
    }
    
    /**
     * Tests the support for read-only resources in the file system (outside
     * of the workspace).
     */
    public void test_readOnlyResourceMap_filesystem_bug156428() {
        final File file;
        
        try {
            file = File.createTempFile("testReadOnly", ".xmi"); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            fail("Failed to create temporary file: " + e.getLocalizedMessage()); //$NON-NLS-1$
            
            // compiler doesn't know that fail() throws
            throw new AssertionFailedError();
        }
        
        addTearDownAction(new Runnable() {
            public void run() {
                delete(file);
            }});
        
        // a resource that doesn't exist should be writable
        Resource res = domain.getResourceSet().createResource(
            URI.createFileURI(file.getAbsolutePath() + "2")); //$NON-NLS-1$
        assertFalse(domain.isReadOnly(res));
        
        domain.getResourceSet().getResources().remove(res);
        
        // a resource that does exist and is writable should be writable
        res = domain.getResourceSet().createResource(
            URI.createFileURI(file.getAbsolutePath()));
        assertFalse(domain.isReadOnly(res));

        domain.getResourceSet().getResources().remove(res);
        
        file.setReadOnly();
        
        // a resource that does exist and is not writable should be read-only
        res = domain.getResourceSet().createResource(
            URI.createFileURI(file.getAbsolutePath()));
        assertTrue(domain.isReadOnly(res));
    }
}
