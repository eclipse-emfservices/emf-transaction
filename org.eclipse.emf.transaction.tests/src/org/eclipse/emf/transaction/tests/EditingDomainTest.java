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
 * $Id: EditingDomainTest.java,v 1.3 2006/12/20 17:06:54 cdamus Exp $
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
}
