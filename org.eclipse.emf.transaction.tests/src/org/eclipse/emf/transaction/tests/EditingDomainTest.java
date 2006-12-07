/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: EditingDomainTest.java,v 1.1 2006/12/07 16:44:48 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;

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
		ReferenceQueue q = new ReferenceQueue();
		
		TransactionalEditingDomain domain =
			TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain();
		ResourceSet rset = domain.getResourceSet();
		
		WeakReference ref = new WeakReference(domain, q);
		
		// check initial conditions
		assertSame(domain, TransactionUtil.getEditingDomain(rset));
		
		// dispose and forget the editing domain
		domain.dispose();
		domain = null;
		
		// verify that the resource set has forgotten its editing domain
		assertNull(TransactionUtil.getEditingDomain(rset));
		
		runGC();
		
		// verify that the domain was reclaimed
		assertSame(ref, q.poll());
	}
	
	//
	// Fixtures
	//

	private void runGC() {
		System.gc();
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			fail("Interrupted"); //$NON-NLS-1$
		}
		
		System.gc();
	}
}
